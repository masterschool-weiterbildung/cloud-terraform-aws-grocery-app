# Grocery App AWS Infrastructure

This Terraform project sets up an AWS infrastructure for a grocery application, including a VPC, public and private subnets, an EC2 instance, and a PostgreSQL RDS database. The configuration follows AWS best practices for security and high availability.

## Architecture Overview

- **VPC**: A Virtual Private Cloud with a CIDR block of '10.0.0.0/16' in the 'eu-central-1' (Frankfurt) region.
- **Subnets**:
  - One public subnet (`10.0.1.0/24`) for the EC2 instance.
  - Two private subnets (`10.0.2.0/24`, `10.0.3.0/24`) for the RDS database, spanning two availability zones ('eu-central-1a', 'eu-central-1b') for high availability.
- **Internet Gateway**: Enables internet access for the public subnet.
- **Route Table**: Routes internet-bound traffic from the public subnet to the Internet Gateway.
- **EC2 Instance**: A single instance in the public subnet with a public IP and SSH key pair ('jerome-aws-frankfurt') for access. Secured by a security group allowing SSH (port 22) and HTTP (port 80).
- **RDS Instance**: A PostgreSQL database ('grocerymate_db') in the private subnets, accessible only from the EC2 instance via a dedicated security group (port 5432).
- **Outputs**: Displays the EC2 instance's public IP for easy access.

## Prerequisites

1. **Terraform**: Version '>= 1.2.0' installed.
2. **AWS CLI**: Configured with credentials for programmatic access.
3. **AWS Key Pair**: An SSH key pair named 'jerome-aws-frankfurt' in the 'eu-central-1' region. Create it in the AWS Management Console (EC2 > Key Pairs) or via Terraform.
4. **Private Key**: The private key file (e.g., 'jerome-aws-frankfurt.pem') for SSH access to the EC2 instance.

