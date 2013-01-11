package de.tototec.osgi.setupbuilder

import java.io.File
import java.util.jar.Manifest
import java.util.jar.JarFile
import java.util.jar.Attributes
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.Properties

class EquinoxSetupBuilder(
  setup: OsgiSetup,
  targetDir: File) {

  private lazy val log = new {
    def debug(msg: => String) { println(msg) }
  }

  def build {
    targetDir.mkdirs

    val pluginDir = new File(targetDir, "plugins")
    pluginDir.mkdirs

    val configDir = new File(targetDir, "configuration")
    configDir.mkdirs

    var equinoxConfig: Map[String, String] = Map()

    setup.bundles.map { file =>
      // the Bundle 
      val bundle = new Bundle(file)

      // the target location of the bundle
      val bundleFile = new File(pluginDir, s"${bundle.symbolicName}_${bundle.version}.jar")

      // copy bundle to target location
      log.debug(s"Copying bundle ${bundle.symbolicName} to plugins directory.")
      val out = new FileOutputStream(bundleFile)
      val in = new FileInputStream(file)
      try {
        out.getChannel.transferFrom(in.getChannel, 0, Long.MaxValue)
      } finally {
        in.close
        out.close
      }

      // Add the bundle to config, either as framework bundle or as plugin with optional start level and start configuration
      if (setup.frameworkBundle == bundle.symbolicName) {
        equinoxConfig += ("osgi.framework" -> s"file:plugins/${bundleFile.getName}")
      } else {
        // the suffix indicates the start level and the start state
        // <URL | simple bundle location>[@ [<start-level>] [":start"]]

        val suffix = setup.bundleConfigs.find(config => config.symbolicName == bundle.symbolicName) match {
          case Some(BundleConfig(_, startLevel, autoStart)) =>
            "@" +
              startLevel.map(level => level.toString).getOrElse("") +
              (if (autoStart) ":start" else "")
          case None => ""
        }

        var bundles = equinoxConfig.get("osgi.bundles").toSeq
        bundles ++= Seq("reference:file:" + bundleFile.getName + suffix)

        equinoxConfig += ("osgi.bundles" -> bundles.mkString(","))
      }

    }

    // copy frameworkSettings info equinox config
    equinoxConfig ++= setup.frameworkSettings

    // write equinox config
    val props = new Properties()
    equinoxConfig.foreach {
      case (key, value) => props.setProperty(key, value)
    }
    val out = new FileOutputStream(new File(configDir, "config.ini"))
    try {
      props.store(out, "Equinox OSGi configuration generated by " + getClass.getName)
    } finally {
      out.close
    }
  }

}
