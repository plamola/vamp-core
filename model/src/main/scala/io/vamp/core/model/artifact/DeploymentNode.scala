package io.vamp.core.model.artifact

/**
 * The Deployment node representation that is the root node in the bi-directional tree.
 *
 * @param deployment The value of the node: Deployment
 */

case class DeploymentNode(deployment: Deployment) {
  def clusters = deployment.clusters.map(DeploymentClusterNode(this, _))
}
