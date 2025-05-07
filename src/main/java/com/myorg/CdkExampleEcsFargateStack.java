package com.myorg;

import java.util.List;
import java.util.Map;

import io.github.cdklabs.cdk.ecr.deployment.DockerImageName;
import io.github.cdklabs.cdk.ecr.deployment.ECRDeployment;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuArchitecture;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.OperatingSystemFamily;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddRuleProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCondition;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

public class CdkExampleEcsFargateStack extends Stack {
    public CdkExampleEcsFargateStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkExampleEcsFargateStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String accountIdFromEnv =  this.getAccount();
    
        new Repository(this, "UserbookRepository", 
            RepositoryProps.builder().repositoryName("userbook-maven").removalPolicy(RemovalPolicy.DESTROY).build());
        
        new Repository(this, "HelloWorldGradleRepository", 
            RepositoryProps.builder().repositoryName("hello-world-rest-gradle").removalPolicy(RemovalPolicy.DESTROY).build());
            
        String userbookDockerHubImageName = "ismaelgueyetraining/userbook-maven:0.0.3-SNAPSHOT";
        String userbookEcrDockerImageName = String.format("%s.dkr.ecr.us-east-1.amazonaws.com/userbook-maven:latest", accountIdFromEnv);
        
        ECRDeployment.Builder.create(this, "DeployDockerImageUserbookMaven")
         .src(new DockerImageName(userbookDockerHubImageName))
         .dest(new DockerImageName(userbookEcrDockerImageName))
         .build(); 

        String helloWorlGradleDockerHubImageName = "ismaelgueyetraining/hello-world-rest-gradle:0.0.2-SNAPSHOT";
        String helloWorldGradleEcrDockerImageName = String.format("%s.dkr.ecr.us-east-1.amazonaws.com/hello-world-rest-gradle:latest", accountIdFromEnv);
        
        ECRDeployment.Builder.create(this, "DeployDockerImageHelloWorldGradle")
         .src(new DockerImageName(helloWorlGradleDockerHubImageName))
         .dest(new DockerImageName(helloWorldGradleEcrDockerImageName))
         .build(); 
        

        Vpc vpc = Vpc.Builder.create(this, "MyVpcForEcsDemo").maxAzs(3).build();
        
        Cluster cluster = Cluster.Builder.create(this, "MyClusterForEcsDemo").vpc(vpc).build();
        
        // Define the execution role
        Role executionRole = Role.Builder.create(this, "TaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(
                    ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonECSTaskExecutionRolePolicy")
                ))
                .build();
                
                // --- Service A (using ApplicationLoadBalancedFargateService) ---
        LogGroup serviceALogGroup = LogGroup.Builder.create(this, "ServiceUserBookMavenLogGroup")
                .retention(RetentionDays.ONE_WEEK)
                .build();
                
        ApplicationLoadBalancedFargateService serviceUserBookMaven = ApplicationLoadBalancedFargateService.Builder.create(this, "MyFargateUserbookService")
                .cluster(cluster)
                .cpu(256)
                .memoryLimitMiB(512)
                .desiredCount(1)
                .taskImageOptions(
                       ApplicationLoadBalancedTaskImageOptions.builder()
                               .image(ContainerImage.fromRegistry(userbookEcrDockerImageName))
                               .containerPort(8081)
                               .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder().streamPrefix("ServiceUserBookMaven").logGroup(serviceALogGroup).build()))
                               .environment(Map.of("SERVICE_NAME", "ServiceUserBookMaven"))
                               .executionRole(executionRole)
                               .build())
                .publicLoadBalancer(true)
                .listenerPort(80)
                .build();
        
     // Configure the health check
        serviceUserBookMaven.getTargetGroup().configureHealthCheck(HealthCheck.builder()
            .path("/userbook/hello") // Set your desired health check path here
            .interval(Duration.seconds(60))
            .timeout(Duration.seconds(5))
            .healthyThresholdCount(2)
            .unhealthyThresholdCount(2)
            .build());
            
            
        // Get the ALB and Listener created by Service A
        ApplicationLoadBalancer alb = serviceUserBookMaven.getLoadBalancer();
        ApplicationListener listener = serviceUserBookMaven.getListener();
        
        // --- Service B (Nginx Hello) ---
        int serviceHelloWorldGradleContainerPort = 8082;
        LogGroup serviceHelloWorlGradleLogGroup = LogGroup.Builder.create(this, "ServiceHelloWorlGradleLogGroup")
                .retention(RetentionDays.ONE_WEEK)
                .build();

        FargateTaskDefinition taskDefHelloWorlGradle = FargateTaskDefinition.Builder.create(this, "TaskDefHelloWorlGradle")
            .cpu(256)
            .memoryLimitMiB(512)
            .runtimePlatform(software.amazon.awscdk.services.ecs.RuntimePlatform.builder()
                    .cpuArchitecture(CpuArchitecture.X86_64)
                    .operatingSystemFamily(OperatingSystemFamily.LINUX)
                    .build())
            .build();

        taskDefHelloWorlGradle.addContainer("AppContainerB", software.amazon.awscdk.services.ecs.ContainerDefinitionOptions.builder()
            .image(ContainerImage.fromRegistry(helloWorldGradleEcrDockerImageName)) // Docker image for Service B
            .portMappings(List.of(PortMapping.builder().containerPort(serviceHelloWorldGradleContainerPort).build()))
            .logging(LogDriver.awsLogs(AwsLogDriverProps.builder().streamPrefix("ServiceB").logGroup(serviceHelloWorlGradleLogGroup).build()))
            .environment(Map.of("SERVICE_NAME", "ServiceB"))
            .build());

        FargateService fargateServiceB = FargateService.Builder.create(this, "FargateServiceHelloWorlGradle")
            .cluster(cluster)
            .taskDefinition(taskDefHelloWorlGradle)
            .desiredCount(1)
            .assignPublicIp(false) // Important: Services behind an ALB don't need public IPs
            .build();

        ApplicationTargetGroup targetGroupB = ApplicationTargetGroup.Builder.create(this, "TargetGroupHelloWorlGradle")
            .vpc(vpc)
            .port(serviceHelloWorldGradleContainerPort)
            .protocol(ApplicationProtocol.HTTP)
            .targets(List.of(fargateServiceB))
            .healthCheck(HealthCheck.builder()
                .path("/helloWorldRestApis/hello ") // Nginx hello serves on /
                .interval(Duration.seconds(60))
                .build())
            .build();

 // Add a rule to the listener to forward traffic for /service-b/* to targetGroupB
        listener.addTargets("ServiceHelloWorlGradleTargetRule", AddApplicationTargetsProps.builder()
            .priority(10) // Must be unique for rules on this listener
            .conditions(List.of(
                ListenerCondition.pathPatterns(List.of("/helloWorldRestApis/*"))
            ))
            .targetGroupName(targetGroupB.getTargetGroupName()) // Specify the target group(s) to forward to
            .build());
            
        // Output the ALB DNS name
        CfnOutput.Builder.create(this, "LoadBalancerDNS")
            .value(alb.getLoadBalancerDnsName())
            .description("DNS name of the Application Load Balancer")
            .build();
            
        CfnOutput.Builder.create(this, "ServiceUserbookMavenEndpointInfo")
            .value("Access Service UserbookMaven (default path): http://" + alb.getLoadBalancerDnsName() + "/userbook/")
            .build();
            
        CfnOutput.Builder.create(this, "ServiceHelloWorldEndpoint")
            .value("Access Service Hello WorldGradle: http://" + alb.getLoadBalancerDnsName() + "/helloWorldRestApis/")
            .build();
    
    }
}
