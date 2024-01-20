package io.scalac.test.gh4s

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import github4s.domain.{Pagination, Repository, User}
import github4s.{GHError, GHResponse, Github}
import io.scalac.test.model.{RepoWithOwner, UserWithContributions}
import org.http4s.client.*

import scala.collection.mutable
import scala.collection.parallel.CollectionConverters.*

object Apis {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
  private val PER_PAGE_DEFAULT = 50 // default: 30; max: 100

  case class GetPageResult[T](result: T, fetchedPages: Int, lastError: Option[GHError])

  /** searches for needed (rel) page number in Option[String] like
    * `<https://api.github.com/organizations/322765/repos?page=2>; rel="next",
    * <https://api.github.com/organizations/322765/repos?page=7>; rel="last"`
    */
  def extractPage(linkContents: Option[String], relToSearch: String): Option[String] = {
    val pagePattern = "(?!.*\\?page=)([\\d]+)".r
    val pattern = s"(?<=<)([\\S]*)(?=>; rel=\"$relToSearch\")".r
    for {
      link <- linkContents
      searchNext <- pattern.findFirstMatchIn(link)
      pageNum <- pagePattern.findFirstMatchIn(searchNext.toString())
    } yield pageNum
  }.map(_.toString())

  def getReposStream(httpClient: Resource[IO, Client[IO]], accessToken: Option[String], orgName: String) = {
    var startFromPage: Option[Int] = None
    val strm: fs2.Stream[IO, GHResponse[List[Repository]]] = for {
      client <- fs2.Stream.resource(httpClient)
      sr <- fs2.Stream.eval(
        Github[IO](client, accessToken).repos.listOrgRepos(
          org = orgName,
          pagination = startFromPage.map(startFrom => Pagination(startFrom, PER_PAGE_DEFAULT)),
          `type` = Some("all")
        )
      )
    } yield sr
    strm.compile.toList.unsafeRunSync()
  }

  /** https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#list-organization-repositories Iterating with
    * chunks of [[PER_PAGE_DEFAULT]] until either next page is not provided, or the error is returned
    * @param client
    *   httpClient
    * @param accessToken
    *   Github access token
    * @param orgName
    *   organization for those repositories
    * @param startFromPage
    * @return
    *   [[GetPageResult]]
    */
  def getRepos(
      client: Resource[IO, Client[IO]],
      accessToken: Option[String],
      orgName: String,
      startFromPage: Option[Int] = None
  ): GetPageResult[Seq[RepoWithOwner]] = {

    /** retrieves the next chunk of org repos list see [[PER_PAGE_DEFAULT]]
      * @param startFromPage
      * @return
      *   [[GHResponse]]
      */
    def getOrgReposInternal(startFromPage: Option[Int] = None): GHResponse[List[Repository]] = client
      .use { client =>
        // !! `type` = Some("all")
        Github[IO](client, accessToken).repos
          .listOrgRepos(
            org = orgName,
            pagination = startFromPage.map(startFrom => Pagination(startFrom, PER_PAGE_DEFAULT)),
            `type` = Some("all")
          )
      }
      .unsafeRunSync()
    //

    var lastError: Option[GHError] = None
    var startFrom: Option[Int] = startFromPage
    var lastPageIndex: Option[String] = None
    var fetchedPages: Int = 0

    var getNextPage = true
    var results: mutable.Seq[RepoWithOwner] = mutable.ListBuffer[RepoWithOwner]()

    while (getNextPage) {

      val queryResult = getOrgReposInternal(startFrom)

      val linkContents = queryResult.headers.get("Link")
      val nextPage = extractPage(linkContents, "next")
      lastPageIndex = extractPage(linkContents, "last")

      queryResult.result match {
        case Left(ghError) => lastError = Some(ghError)
        case Right(orgRepos: List[Repository]) =>
          results ++= orgRepos.map(repo => RepoWithOwner(repo.name, repo.owner.login))
          fetchedPages += 1
          startFrom = Some(fetchedPages + 1)
      }
      getNextPage = lastError.isEmpty && nextPage.isDefined

    }
    GetPageResult(result = results.toList, fetchedPages = fetchedPages, lastError = lastError)

  }

  /** Retrieves contributors of specific repo Iterating with chunks of [[PER_PAGE_DEFAULT]] until either next page is
    * not provided, or the error is returned
    * @param client
    *   httpClient
    * @param accessToken
    *   github access token
    * @param from
    *   repository (name + owner login)
    * @return
    */
  def getRepoContributors(
      client: Resource[IO, Client[IO]],
      accessToken: Option[String],
      from: RepoWithOwner
  ): GetPageResult[Seq[UserWithContributions]] = {
    //
    def getRepoContributorsInternal(
        ownerLogin: String,
        repoName: String,
        startFromPage: Option[Int] = None
    ): GHResponse[List[User]] = {
      client
        .use { client =>
          Github[IO](client, accessToken).repos.listContributors(
            ownerLogin,
            repoName,
            pagination = startFromPage.map(startFrom => Pagination(startFrom, PER_PAGE_DEFAULT))
          )
        }
        .unsafeRunSync()
    }

    val (repoName: String, repoOwner: String) = (from.repoName, from.ownerLogin)

    var results: mutable.Seq[UserWithContributions] = mutable.ListBuffer[UserWithContributions]()
    var lastError: Option[GHError] = None
    var startFrom: Option[Int] = None
    var lastPageIndex: Option[String] = None
    var fetchedPages: Int = 0

    var getNextPage = true

    while (getNextPage) {

      val queryResult = getRepoContributorsInternal(repoOwner, repoName, startFrom)
      val linkContents = queryResult.headers.get("Link")
      val nextPage = extractPage(linkContents, "next")
      lastPageIndex = extractPage(linkContents, "last")

      queryResult.result match {
        case Left(ghError) => {
          lastError = Some(ghError)
        }
        case Right(users: List[User]) =>
          results ++= {
            for {
              user <- users
              userName = user.login
              contributions <- user.contributions
            } yield UserWithContributions(userName, contributions)
          }
          fetchedPages += 1
          startFrom = Some(fetchedPages + 1)
      }
      getNextPage = lastError.isEmpty && nextPage.isDefined
    }
    GetPageResult(result = results.toSeq, fetchedPages = fetchedPages, lastError = lastError)
  }

  /** Iterates over list of repos and fetches corresponding contributors
    * @param client
    * @param accessToken
    * @param reposList
    *   Seq[[RepoWithOwner]] - repo name + owner login
    * @return
    */
  def getContributors(
      client: Resource[IO, Client[IO]],
      accessToken: Option[String],
      reposList: Seq[RepoWithOwner]
  ): GetPageResult[Seq[UserWithContributions]] = {
    def getContributionsInternal(repoOwner: String, repoName: String, startFromPage: Option[Int] = None) =
      client
        .use { client =>
          Github[IO](client, accessToken).repos.listContributors(
            repoOwner,
            repoName,
            pagination = startFromPage.map(startFrom => Pagination(startFrom, PER_PAGE_DEFAULT))
          )
        }
        .unsafeRunSync()

    var results: mutable.Seq[UserWithContributions] = mutable.ListBuffer[UserWithContributions]()
    var totalFetchedPages: Int = 0
    var totalLastError: Option[GHError] = None
    //

    reposList.par.foreach { case rown @ RepoWithOwner(repoName: String, repoOwner: String) =>
      val repoReslt = getRepoContributors(client, accessToken, rown)
      results ++= repoReslt.result
      totalLastError = repoReslt.lastError
      totalFetchedPages += repoReslt.fetchedPages
      var lastError: Option[GHError] = None
      var startFrom: Option[Int] = None
      var lastPageIndex: Option[String] = None
      var fetchedPages: Int = 0

      var getNextPage = true

    }

    GetPageResult(result = results.toList, fetchedPages = totalFetchedPages, lastError = totalLastError)
  }
}
