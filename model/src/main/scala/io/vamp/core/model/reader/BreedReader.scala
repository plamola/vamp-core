package io.vamp.core.model.reader

import io.vamp.core.model.artifact._
import io.vamp.core.model.notification._

import scala.language.postfixOps

object BreedReader extends YamlReader[Breed] with ReferenceYamlReader[Breed] {

  override def readReference(any: Any): Breed = any match {
    case reference: String => BreedReference(reference)
    case map: collection.Map[_, _] =>
      implicit val source = map.asInstanceOf[YamlObject]
      <<?[Any]("deployable") match {
        case None => BreedReference(name)
        case Some(_) => read(map.asInstanceOf[YamlObject])
      }
  }

  override protected def expand(implicit source: YamlObject) = {
    expandToList("ports")
    expandToList("environment_variables")

    <<?[YamlObject]("dependencies") match {
      case None =>
      case Some(map) => map.map {
        case (alias: String, dependency: Any) => dependency match {
          case reference: String => >>("dependencies" :: alias :: "breed" :: "name", dependency)
          case map: collection.Map[_, _] => map.asInstanceOf[YamlObject].get("breed") match {
            case None => >>("dependencies" :: alias :: "breed", dependency)
            case Some(breed) =>
          }
        }
      }
    }

    super.expand
  }

  override protected def parse(implicit source: YamlObject): Breed = {

    val deployable = new Deployable(<<![String]("deployable"))

    val ports = <<?[YamlList]("ports") match {
      case None => List[Port]()
      case Some(list: YamlList) => list.map {
        port =>
          implicit val source = port
          Port.toPort(name, <<?[String]("alias"), <<?[String]("value"), Trait.Direction.withName(<<![String]("direction").toLowerCase.capitalize))
      }
    }

    val environmentVariables = <<?[YamlList]("environment_variables") match {
      case None => List[EnvironmentVariable]()
      case Some(list: YamlList) => list.map {
        environmentVariable =>
          implicit val source = environmentVariable
          EnvironmentVariable(name, <<?[String]("alias"), <<?[String]("value"), Trait.Direction.withName(<<![String]("direction").toLowerCase.capitalize))
      }
    }

    val dependencies = <<?[YamlObject]("dependencies") match {
      case None => Map[String, Breed]()
      case Some(map) => map.map {
        case (alias: String, dependency: collection.Map[_, _]) =>
          (alias, readReference(dependency.asInstanceOf[YamlObject].get("breed").get))
      } toMap
    }

    DefaultBreed(name, deployable, ports, environmentVariables, dependencies)
  }

  override protected def validate(any: Breed): Breed = any match {
    case breed: BreedReference => breed
    case breed: DefaultBreed =>

      breed.traits.map(_.name).find({
        case Trait.Name(None, Some(group), value) => true
        case Trait.Name(Some(scope), None, value) => value != Trait.host
        case _ => false
      }).flatMap {
        name => error(MalformedTraitNameError(breed, name))
      }

      breed.ports.filter(_.direction == Trait.Direction.Out).find(_.value.isEmpty).flatMap {
        port => error(MissingPortValueError(breed, port))
      }

      breed.environmentVariables.filter(_.direction == Trait.Direction.Out).find(_.value.isEmpty).flatMap {
        environmentVariables => error(MissingEnvironmentVariableValueError(breed, environmentVariables))
      }

      breed.ports.groupBy(_.name.toString).collect {
        case (name, ports) if ports.size > 1 => error(NonUniquePortNameError(breed, ports.head))
      }

      breed.environmentVariables.groupBy(_.name.toString).collect {
        case (name, environmentVariables) if environmentVariables.size > 1 => error(NonUniqueEnvironmentVariableNameError(breed, environmentVariables.head))
      }

      breed.traits.map(_.name).find({
        case Trait.Name(Some(scope), group, value) => breed.dependencies.get(scope) match {
          case None => true
          case Some(dependency: BreedReference) => false
          case Some(dependency: DefaultBreed) => group match {
            case None => false
            case Some(g) if g == Trait.Name.Group.Ports => dependency.ports.forall(_.name.toString != value)
            case Some(g) if g == Trait.Name.Group.EnvironmentVariables => dependency.environmentVariables.forall(_.name.toString != value)
            case _ => true
          }
          case _ => false
        }
        case _ => false
      }).flatMap {
        name => error(UnresolvedDependencyForTraitError(breed, name))
      }

      validateNonRecursiveDependencies(breed)

      breed
  }

  def validateNonRecursiveDependencies(breed: Breed): Unit = {

    recursive(breed, Set(breed.name))

    def recursive(breed: Breed, visited: Set[String]): Unit = breed match {
      case db: DefaultBreed => db.dependencies.foreach { dependency =>
        if (visited.contains(dependency._2.name))
          error(RecursiveDependenciesError(breed))
        else
          recursive(dependency._2, visited + dependency._2.name)
      }
      case _ =>
    }
  }
}