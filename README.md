Offers a `Refreshable` type that operates like a cache of size 1 with a background fiber that periodically refreshes the stored value

---

- [Installation](#installation)
- [Usage](#usage)
- [Contributors to this project](#contributors-to-this-project)

## Installation

Add the following line to your `build.sbt` file:

```sbt
libraryDependencies += "com.permutive" %% "refreshable" % "2.0.0"
```

The library is published for Scala versions: `2.12`, `2.13` and `3`.

## Usage

# Refreshable

`Refreshable` lives in the [Typelevel](https://typelevel.org/) Scala ecosystem
and offers a `Refreshable` type that operates like a cache of size 1 with a
background fiber that periodically refreshes the stored value. Use it when you
have criticial data that needs to be cached and you would rather read stale data
in the event that refreshing the data fails.

```scala
trait Refreshable[F[_], A] {

  /** Get the unwrapped value of `A`
    */
  def value: F[A] = get.map(_.value)

  /** Get the value of `A` wrapped in a status
    */
  def get: F[CachedValue[A]]

  /** Cancel refreshing
    */
  def cancel: F[Boolean]

  /** Restart refreshing
    */
  def restart: F[Boolean]
}

sealed trait CachedValue[A] {
  def value: A
}

object CachedValue {
  case class Success[A](value: A) extends CachedValue[A]
  case class Error[A](value: A, error: Throwable) extends CachedValue[A]
  case class Cancelled[A](value: A) extends CachedValue[A]
}
```

## Contributors to this project

| <a href="https://github.com/TimWSpence"><img alt="TimWSpence" src="https://avatars.githubusercontent.com/u/3360080?v=4&s=120" width="120px" /></a> | <a href="https://github.com/janstenpickle"><img alt="janstenpickle" src="https://avatars.githubusercontent.com/u/1926225?v=4&s=120" width="120px" /></a> | <a href="https://github.com/alejandrohdezma"><img alt="alejandrohdezma" src="https://avatars.githubusercontent.com/u/9027541?v=4&s=120" width="120px" /></a> |
| :--: | :--: | :--: |
| <a href="https://github.com/TimWSpence"><sub><b>TimWSpence</b></sub></a> | <a href="https://github.com/janstenpickle"><sub><b>janstenpickle</b></sub></a> | <a href="https://github.com/alejandrohdezma"><sub><b>alejandrohdezma</b></sub></a> |