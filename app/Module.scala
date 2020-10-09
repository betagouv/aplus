import _root_.play.api.Environment
import com.google.inject.{AbstractModule, Provides}
import com.hhandoko.play.pdf.PdfGenerator
import net.codingwell.scalaguice.ScalaModule

class Module extends AbstractModule with ScalaModule {

  /** Module configuration + binding */
  override def configure(): Unit = ()

  /**
    * Provides PDF generator implementation.
    *
    * @param env The current Play app Environment context.
    * @return PDF generator implementation.
    */
  @Provides
  def providePdfGenerator(env: Environment): PdfGenerator = new PdfGenerator(env)

}
