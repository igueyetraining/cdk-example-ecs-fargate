package com.myorg;

import java.util.List;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class CdkExampleEcsFargateStack extends Stack {
    public CdkExampleEcsFargateStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkExampleEcsFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = Vpc.Builder.create(this, "MyVpcForEcsDemo").maxAzs(3).build();

        Cluster cluster = Cluster.Builder.create(this, "MyClusterForEcsDemo")
                .vpc(vpc).build();
                
//        String accountIdFromEnv =  this.getAccount(); //975050070855
        Stack stack = Stack.of(this);
        String accountIdFromEnv = stack.getAccount();
//    String accountId =  "12312312399";
        
//        TaskDefinition taskDefinition = new TaskDefinition(scope, id, null);
//        taskDefinition.getExecutionRole().addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonEC2ContainerRegistryPowerUser"));

//        ContainerDefinition container = taskDefinition.addContainer("MyContainer", ContainerDefinition.Builder.create()
//            .image(ContainerImage.fromRegistry("my-image"))
//            .build());
//
//        container.addPortMappings(PortMapping.builder()
//            .containerPort(8080)  // The port the application listens on in the container
//            .hostPort(8080)       // The port the host listens on
//            .build());

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
                .desiredCount(6)
                .taskImageOptions(
                       ApplicationLoadBalancedTaskImageOptions.builder()
                               .image(ContainerImage.fromRegistry(accountIdFromEnv + ".dkr.ecr.us-east-1.amazonaws.com/userbook-maven:latest"))
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
