package com.myorg;

import software.amazon.awscdk.CfnTag;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.secretsmanager.*;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GroceryAppStack extends Stack {
    public GroceryAppStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public GroceryAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        final String extraTag = "extra-tag";

        // VPC
        Vpc vpc = Vpc.Builder.create(this, "GroceryAppVpc")
                .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
                .maxAzs(2)
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder()
                                .name("Public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("Private")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .cidrMask(24)
                                .build()
                ))
                .build();
        software.amazon.awscdk.Tags.of(vpc).add("Name", "grocery-app-vpc");
        vpc.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Public Subnet
        ISubnet publicSubnet = vpc.getPublicSubnets().get(0);
        software.amazon.awscdk.Tags.of(publicSubnet).add("Name", "public-grocery-app-subnet");

        // Private Subnets
        ISubnet privateSubnetA = vpc.getPrivateSubnets().get(0);
        ISubnet privateSubnetB = vpc.getPrivateSubnets().get(1);
        privateSubnetA.applyRemovalPolicy(RemovalPolicy.DESTROY);
        privateSubnetB.applyRemovalPolicy(RemovalPolicy.DESTROY);

        software.amazon.awscdk.Tags.of(privateSubnetA).add("Name", "private-grocery-app-subnet-A");
        software.amazon.awscdk.Tags.of(privateSubnetB).add("Name", "private-grocery-app-subnet-B");


        SubnetGroup dbsubnetGroup = SubnetGroup.Builder.create(this, "DBGrocerySubnetGroup")
                .description("DB Grocery app subnet group")
                .vpc(vpc)
                .removalPolicy(RemovalPolicy.DESTROY)
                .subnetGroupName("db-grocery-subnet-group")
                .vpcSubnets(SubnetSelection.builder()
                        .availabilityZones(Arrays.asList("eu-central-1a", "eu-central-1b"))
                        .onePerAz(false)
                        .subnetGroupName("db_grocery_subnet_group")
                        .subnets(Arrays.asList(privateSubnetA, privateSubnetB))
                        .subnetType(SubnetType.PRIVATE_ISOLATED)
                        .build())
                .build();

        software.amazon.awscdk.Tags.of(dbsubnetGroup).add("Name", "db-grocery-subnet-group");
        dbsubnetGroup.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Internet Gateway
        CfnInternetGateway cfnInternetGateway = CfnInternetGateway.Builder.create(this, "grocery-app-gw")
                .tags(List.of(CfnTag.builder()
                        .key("Name")
                        .value("grocery-app-igw")
                        .build()))
                .build();

        software.amazon.awscdk.Tags.of(cfnInternetGateway).add("Name", "grocery-app-gw");
        cfnInternetGateway.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Attach Internet Gateway to VPC
        CfnVPCGatewayAttachment attachment = CfnVPCGatewayAttachment.Builder.create(this, "VpcIgwAttachment")
                .vpcId(vpc.getVpcId())
                .internetGatewayId(cfnInternetGateway.getRef())
                .build();

        software.amazon.awscdk.Tags.of(attachment).add("Name", "vpc-igw-attachment");
        attachment.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // Route Table Route
        CfnRoute.Builder.create(this, "PublicRoute")
                .routeTableId(vpc.getPublicSubnets().get(0).getRouteTable().getRouteTableId())
                .destinationCidrBlock("0.0.0.0/0")
                .gatewayId(cfnInternetGateway.getRef())
                .build();

        // EC2 Security Group
        SecurityGroup ec2Sg = SecurityGroup.Builder.create(this, "Ec2Sg")
                .securityGroupName("grocery-app-ec2-sg")
                .description("Security group for grocery app EC2 instance")
                .vpc(vpc)
                .build();

        ec2Sg.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "Allow SSH from anywhere");
        ec2Sg.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow HTTP from anywhere");
        ec2Sg.addEgressRule(Peer.anyIpv4(), Port.allTraffic(), "Allow all outbound");

        software.amazon.awscdk.Tags.of(ec2Sg).add("Name", "grocery-app-ec2-sg");
        ec2Sg.applyRemovalPolicy(RemovalPolicy.DESTROY);

        // RDS Security Group
        SecurityGroup rdsSg = SecurityGroup.Builder.create(this, "RdsSg")
                .securityGroupName("grocery-app-rds-sg")
                .description("Security group for grocery app RDS instance")
                .vpc(vpc)
                .build();

        rdsSg.addIngressRule(ec2Sg, Port.tcp(5432), "Allow PostgreSQL from EC2 security group");
        rdsSg.addEgressRule(Peer.anyIpv4(), Port.allTraffic(), "Allow all outbound");
        
        software.amazon.awscdk.Tags.of(rdsSg).add("Name", "grocery-app-rds-sg");
        rdsSg.applyRemovalPolicy(RemovalPolicy.DESTROY);

        IKeyPair keyPair = KeyPair.Builder.create(this, "KeyPair")
                .keyPairName("jerome-aws-frankfurt")
                .build();

        // EC2 Instance
        Instance instance = Instance.Builder.create(this, "Instance")
                .instanceName("grocery-app-instance")
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .machineImage(MachineImage.latestAmazonLinux2023())
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroup(ec2Sg)
                .keyPair(keyPair)
                .build();

        software.amazon.awscdk.Tags.of(instance).add("ExtraTag", extraTag);
        instance.applyRemovalPolicy(RemovalPolicy.DESTROY);

        ISecret dbCredentialsSecret = Secret.fromSecretNameV2(this, "DbCredentialsSecret", "grocerymate-db-credentials");

        // RDS Instance
        DatabaseInstance dbInstance = DatabaseInstance.Builder.create(this, "DbInstance")
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_14_17)
                                .build()
                ))
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
                .allocatedStorage(20)
                .storageType(StorageType.GP2)
                .databaseName("grocerymate_db")
                .vpc(vpc)
                .subnetGroup(dbsubnetGroup)
                .securityGroups(Collections.singletonList(rdsSg))
                .deleteAutomatedBackups(true)
                .credentials(Credentials.fromSecret(dbCredentialsSecret))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }
}
