package io.scalac.test

import cats.effect.IO
import cats.implicits.*
import io.scalac.test.gh4s.Apis.GetPageResult
import io.scalac.test.model.{RepoWithOwner, ScalacServiceReply, UserWithContributions}
import org.http4s.ember.client.EmberClientBuilder

object Logics {

  private val httpClient = EmberClientBuilder.default[IO].build
  private val accessToken = sys.env.get("GH_TOKEN")

  import io.scalac.test.gh4s.Apis.{getContributors, getRepos}

  def githubInfoLogic(orgName: String): ScalacServiceReply = {

    val reposResult: GetPageResult[Seq[RepoWithOwner]] = getRepos(httpClient, accessToken, orgName)

    if (reposResult.lastError.isDefined) {
      ScalacServiceReply(
        s"""$orgName repositories not fetched (${reposResult.fetchedPages} pages) because of:
            ${reposResult.lastError.get.getMessage()}""",
        List.empty
      )
    } else {
      val reposList = reposResult.result
      val contribList: GetPageResult[Seq[UserWithContributions]] = getContributors(httpClient, accessToken, reposList)

      if (contribList.lastError.isDefined) {
        ScalacServiceReply(
          s"""$orgName repos contributors not fully fetched (${contribList.fetchedPages.toString} pages) because of:" +
          s"${contribList.lastError.get.getMessage()}""",
          contribList.result.sortBy(-_.contributions)
        )
      } else {
        ScalacServiceReply(s"Done: ${contribList.result.length}", contribList.result.sortBy(-_.contributions))
      }
    }

  }
}
