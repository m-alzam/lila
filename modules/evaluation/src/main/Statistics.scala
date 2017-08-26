package lila.evaluation

import scala.concurrent.duration._

import chess.Centis
import lila.common.{ Maths, Stats }

object Statistics {
  import scala.annotation._

  // Coefficient of Variance
  def coefVariation(a: List[Int]): Float = {
    val s = Stats(a)
    s.stdDev / s.mean
  }

  // ups all values by 0.5s
  // as to avoid very high variation on bullet games
  // where all move times are low (https://lichess.org/@/AlisaP?mod)
  def moveTimeCoefVariation(a: List[Centis]): Double =
    coefVariation(a.map(_.centis + 50))

  def moveTimeCoefVariation(pov: lila.game.Pov): Option[Double] =
    for {
      mt <- pov.game.moveTimes(pov.color) if (mt.nonEmpty)
    } yield moveTimeCoefVariation(mt)

  def consistentMoveTimes(pov: lila.game.Pov): Boolean =
    moveTimeCoefVariation(pov) ?? (_ < 0.4)

  private val fastMove = Centis(50)
  def noFastMoves(pov: lila.game.Pov): Boolean = {
    val moveTimes = ~pov.game.moveTimes(pov.color)
    moveTimes.count(fastMove >) <= (moveTimes.size / 20) + 2
  }

  def listAverage[T: Numeric](x: List[T]): Double =
    if (x.isEmpty) 0 else Maths.mean(x)

  def listDeviation[T: Numeric](x: List[T]): Double =
    if (x.isEmpty) 0 else Stats(x).stdDev
}