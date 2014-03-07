package momijikawa.p2pscalaproto

/**
 * ログ機能をうまくmockするためのtype。
 */
object LoggerLikeObject {

  type LoggerLike = {
    def debug(message: String): Unit
    def error(message: String): Unit
    def info(message: String): Unit
    def warning(message: String): Unit
  }

  class DummyLogger {
    def debug(message: String) = {}
    def error(message: String) = {}
    def info(message: String) = {}
    def warning(message: String) = {}
  }

}