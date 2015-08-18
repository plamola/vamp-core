package io.vamp.core.model.artifact

/**
 * The DeploymentCluster node representation to use in the bi-directional tree.
 *
 * @param deployment The parent of the node.
 * @param cluster The value of the node: DeploymentCluster
 */
case class DeploymentClusterNode(deployment: DeploymentNode, cluster: DeploymentCluster) {
  def services = cluster.services.map(DeploymentServiceNode(this, _))

  def variableIsResolved(breedVarName: String): Boolean = deployment.deployment.variableIsResolved(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.EnvironmentVariables), breedVarName).reference)
}
