package laph

import java.io.{FileWriter, File}
import sys.process.{Process, ProcessLogger}
import laph.Templates.LibratoChartView

object LibratoChart {

  def createChart = writeChartHTML _ andThen runPhantomjs

  def writeChartHTML(libratoView: LibratoChartView) = {
    val tmpFile = File.createTempFile("chart", ".html")
    val writer = new FileWriter(tmpFile)
    writer.write(libratoView.render)
    writer.close()
    tmpFile
  }

  def runPhantomjs(file: File): File = {
    val tmpFile = File.createTempFile("chart", ".png")
    val proc = Process("bin/librato-chart file://" + file.getAbsolutePath + " " + tmpFile.getAbsolutePath)
    proc.!(ProcessLogger(println, println))
    tmpFile
  }

}
