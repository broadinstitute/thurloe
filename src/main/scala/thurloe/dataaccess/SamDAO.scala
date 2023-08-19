package thurloe.dataaccess

import org.broadinstitute.dsde.workbench.client.sam

trait SamDAO {
  def getUserById(userId: String): List[sam.model.User]

}
