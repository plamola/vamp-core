package io.vamp.core.model.artifact

/**
 * The DeploymentService node representation to use in the bi-directional tree.
 *
 * @param deploymentCluster The parent of the node.
 * @param service The value of the node: DeploymentService
 */
case class DeploymentServiceNode(deploymentCluster: DeploymentClusterNode, service: DeploymentService) {
  def hasDependenciesDeployed: Boolean = deploymentCluster.deployment.deployment.areDependenciesDeployed(service.breed.dependencies.values)

  def hasEnvironmentVariablesResolved: Boolean = service.breed.environmentVariables.size == service.environmentVariables.size &&
    service.breed.environmentVariables.map(_.name).forall(deploymentCluster.variableIsResolved)
}
