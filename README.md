## Task

GitHub portal is centered around organizations and repositories. Each organization has many
repositories and each repository has many contributors. Your goal is to create an endpoint that given
the name of the organization will return a list of contributors sorted by the number of contributions.


The endpoint should:

* use GitHub REST API v3 (https://developer.github.com/v3/)
* return a list sorted by the number of contributions made by the developer to all repositories for the
given organization.
* respond to a GET request at port 8080 and address /org/{org_name}/contributors
respond with JSON, which should be a list of contributors in the following format: { “name”:
<contributor_login>,
“contributions”: <no_of_contributions> }
* handle GitHub’s API rate limit restriction using a token that can be set as an environment variable
of name GH_TOKEN
* friendly hint: GitHub API returns paginated responses. You should take it into account if you want
the result to be accurate for larger organizations.

Judging criteria (top the most important)

* finished working sample (usable API, clear instructions on how to run and use)
* code quality (idiomatic Scala, functional programming preferred)
* design quality (proper abstractions)
* tests
* taking advantage of the framework in use
* HTTP protocol usage (response codes, headers, caching, etc.)
* performance
* documented code (where it’s relevant)


## Solution

* used [Tapir](https://tapir.softwaremill.com/en/latest/) for
  * endpoint description
  * OpenAPI stuff generation
  * ...including Swagger dashboard for easy testing
* after writing some own implementations using [EmberClient](https://http4s.org/v1.0/api/org/http4s/ember/client/EmberClientBuilder.html), decided to use [github4s](https://github.com/47degrees/github4s) as github client

```shell
$ set GH_TOKEN <gh._...> sbt run

[info] running (fork) io.scalac.test.Main 
[info] INFO  o.h.e.s.EmberServerBuilderCompanionPlatform - Ember-Server service bound to address: [::]:8080
[info] Go to http://localhost:8080/docs to open SwaggerUI. Press ENTER key to exit.
```
