package io.vamp.core.model.artifact

/**
 * Converters to make it easy to work with the bi-directional tree representation of the Deployment
 */
object GraphConverters {
  import scala.language.implicitConversions
  implicit def unwrapDeployment(deploymentNode: DeploymentNode): Deployment = deploymentNode.deployment
  implicit def unwrapCluster(deploymentClusterNode: DeploymentClusterNode): DeploymentCluster = deploymentClusterNode.cluster
  implicit def unwrapService(deploymentServiceNode: DeploymentServiceNode): DeploymentService = deploymentServiceNode.service
  implicit def deployment2DeploymentNode(deployment: Deployment): DeploymentNode = DeploymentNode(deployment)

}
