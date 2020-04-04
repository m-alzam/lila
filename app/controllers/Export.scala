package controllers

import akka.stream.scaladsl._
import akka.util.ByteString
import scala.concurrent.duration._
import play.api.mvc.Result

import chess.Color
import lila.app._
import lila.common.HTTPRequest
import lila.game.Pov

final class Export(env: Env) extends LilaController(env) {

  private val ExportImageRateLimitGlobal = new lila.memo.RateLimit[String](
    credits = 600,
    duration = 1 minute,
    name = "export image global",
    key = "export.image.global"
  )
  private val ExportGifRateLimitGlobal = new lila.memo.RateLimit[String](
    credits = 240,
    duration = 1 minute,
    name = "export gif global",
    key = "export.gif.global"
  )

  def gif(id: String, color: String) = Open { implicit ctx =>
    OnlyHumansAndFacebookOrTwitter {
      ExportRateLimitGlobal("-", msg = HTTPRequest.lastRemoteAddress(ctx.req).value) {
        OptionFuResult(env.game.gameRepo gameWithInitialFen id) {
          case (game, initialFen) =>
            val pov = Pov(game, Color(color) | Color.white)
            env.game.gifExport.fromPov(pov, initialFen) map
              stream("image/gif") map
              gameImageCacheSeconds(game)
        }
      }
    }
  }

  def legacyGameThumbnail(id: String) = Action {
    MovedPermanently(routes.Export.gameThumbnail(id).url)
  }

  def gameThumbnail(id: String) = Open { implicit ctx =>
    ExportRateLimitGlobal("-", msg = HTTPRequest.lastRemoteAddress(ctx.req).value) {
      OptionFuResult(env.game.gameRepo game id) { game =>
        env.game.gifExport.gameThumbnail(game) map
          stream("image/gif") map
          gameImageCacheSeconds(game)
      }
    }
  }

  def legacyPuzzleThumbnail(id: Int) = Action {
    MovedPermanently(routes.Export.puzzleThumbnail(id).url)
  }

  def puzzleThumbnail(id: Int) = Open { implicit ctx =>
    ExportRateLimitGlobal("-", msg = HTTPRequest.lastRemoteAddress(ctx.req).value) {
      OptionFuResult(env.puzzle.api.puzzle find id) { puzzle =>
        env.game.gifExport.thumbnail(
          fen = chess.format.FEN(puzzle.fenAfterInitialMove | puzzle.fen),
          lastMove = puzzle.initialMove.uci.some,
          orientation = puzzle.color
        ) map stream("image/gif") map { res =>
          res.withHeaders(CACHE_CONTROL -> "max-age=86400")
        }
      }
    }
  }

  private def gameImageCacheSeconds(game: lila.game.Game)(res: Result): Result = {
    val cacheSeconds =
      if (game.finishedOrAborted) 3600 * 24
      else 10
    res.withHeaders(CACHE_CONTROL -> s"max-age=$cacheSeconds")
  }

  private def stream(contentType: String)(stream: Source[ByteString, _]) =
    Ok.chunked(stream).withHeaders(noProxyBufferHeader) as contentType
}
