package demo

import javax.inject.{Inject, Singleton}
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import utils.Logger

import scala.beans.BeanProperty
import scala.io.Source
import scala.util.Try

@Singleton
class DemoParser @Inject() extends Logger {

  def parse(path: String): Try[Demo] = Try {

    val stream = Source.fromResource(path).reader()
    val yaml = new Yaml(new Constructor(classOf[Demo]))
    yaml.load[Demo](stream)
  }
}

class Demo {
  @BeanProperty var groups: Array[String] = Array[String]()
  @BeanProperty var forms: java.util.HashMap[String, java.util.HashMap[String, Array[String]]] =
    new java.util.HashMap[String, java.util.HashMap[String, Array[String]]]()
  @BeanProperty var projects: Array[DemoProject] = Array[DemoProject]()
  @BeanProperty var relations: Array[DemoRelation] = Array[DemoRelation]()
}

class DemoProject {
  @BeanProperty var name: String = null
  @BeanProperty var auditor: String = null
}

class DemoRelation {
  @BeanProperty var project: String = null
  @BeanProperty var from: String = null
  @BeanProperty var form: String = null
  @BeanProperty var to: String = null
}
