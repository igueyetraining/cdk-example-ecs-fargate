package com.myorg;

import java.util.List;

import io.github.cdklabs.cdk.ecr.deployment.DockerImageName;
import io.github.cdklabs.cdk.ecr.deployment.ECRDeployment;
import io.github.cdklabs.cdk.ecr.deployment.ECRDeploymentProps;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

public class CdkExampleEcsFargateStack extends Stack {
    public CdkExampleEcsFargateStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkExampleEcsFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String accountIdFromEnv =  this.getAccount();
    
        new Repository(this, "MyDestinationRepository", 
            RepositoryProps.builder().repositoryName("userbook-maven").removalPolicy(RemovalPolicy.DESTROY).build());
            
        String dockerHubImageName = "ismaelgueyetraining/userbook-maven:0.0.3-SNAPSHOT";
        String ecrDockerImageName = String.format("%s.dkr.ecr.us-east-1.amazonaws.com/userbook-maven:latest", accountIdFromEnv);
        
        ECRDeployment.Builder.create(this, "DeployDockerImage2")
         .src(new DockerImageName(dockerHubImageName))
         .dest(new DockerImageName(ecrDockerImageName))
         .build();
        

        Vpc vpc = Vpc.Builder.create(this, "MyVpcForEcsDemo").maxAzs(3).build();

        Cluster cluster = Cluster.Builder.create(this, "MyClusterForEcsDemo")
                .vpc(vpc).build();
        // Define the execution role
        Role executionRole = Role.Builder.create(this, "TaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                ))
                .build();
                
        ApplicationLoadBalancedFargateService loadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateService")
                .cluster(cluster)
                .cpu(512)
                .desiredCount(2)
                .taskImageOptions(
                       ApplicationLoadBalancedTaskImageOptions.builder()
                               .image(ContainerImage.fromRegistry(ecrDockerImageName))
                               .containerPort(8081)
                               .executionRole(executionRole)
                               .build())
                .memoryLimitMiB(2048)
                .publicLoadBalancer(true).build();
        
     // Configure the health check
        loadBalancedFargateService.getTargetGroup().configureHealthCheck(HealthCheck.builder()
            .path("/userbook/hello") // Set your desired health check path here
            .build());
            
    
    
    }
}
