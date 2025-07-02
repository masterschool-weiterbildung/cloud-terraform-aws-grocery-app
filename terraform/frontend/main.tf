terraform {
required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.16"
    }
  }

  required_version = ">= 1.2.0"
}

provider "aws" {
  region = "eu-central-1"
}

locals {
  extra_tag = "extra-tag"
}

resource "aws_vpc" "grocery_app_vpc" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "grocery-app-vpc"
  }
}

resource "aws_subnet" "public_grocery_app_subnet_a" {
  vpc_id            = aws_vpc.grocery_app_vpc.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = "eu-central-1a"

  tags = {
    Name = "public-grocery-app-subnet"
  }
}

resource "aws_subnet" "private_grocery_app_subnet_a" {
  vpc_id            = aws_vpc.grocery_app_vpc.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "eu-central-1a"

  tags = {
    Name = "private-grocery-app-subnet"
  }
}

resource "aws_subnet" "private_grocery_app_subnet_b" {
  vpc_id            = aws_vpc.grocery_app_vpc.id
  cidr_block        = "10.0.3.0/24"
  availability_zone = "eu-central-1b"

  tags = {
    Name = "private-grocery-app-subnet"
  }
}

resource "aws_db_subnet_group" "grocery_subnet_group" {
  name       = "grocery_subnet_group"
  subnet_ids = [aws_subnet.private_grocery_app_subnet_a.id, aws_subnet.private_grocery_app_subnet_b.id]
  tags = {
    Name = "grocery app subnet group"
  }
}

resource "aws_internet_gateway" "grocery_app_igw" {
  vpc_id = aws_vpc.grocery_app_vpc.id
  tags = {
    Name = "grocery-app-igw"
  }
}

resource "aws_route_table" "public_route_table" {
  vpc_id = aws_vpc.grocery_app_vpc.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.grocery_app_igw.id
  }
  tags = {
    Name = "public-route-table"
  }
}

resource "aws_route_table_association" "public_subnet_association" {
  subnet_id      = aws_subnet.public_grocery_app_subnet_a.id
  route_table_id = aws_route_table.public_route_table.id
}

resource "aws_security_group" "ec2_sg" {
  name        = "grocery-app-ec2-sg"
  description = "Security group for grocery app EC2 instance"
  vpc_id      = aws_vpc.grocery_app_vpc.id

  ingress {
    description = "Allow SSH from anywhere"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Allow HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "grocery-app-ec2-sg"
  }
}

resource "aws_security_group" "rds_sg" {
  name        = "grocery-app-rds-sg"
  description = "Security group for grocery app RDS instance"
  vpc_id      = aws_vpc.grocery_app_vpc.id

  ingress {
    description     = "Allow PostgreSQL from EC2 security group"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2_sg.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "grocery-app-rds-sg"
  }
}

resource "aws_instance" "instance" {
  ami           = var.ami
  instance_type = var.instance_type
  subnet_id     = aws_subnet.public_grocery_app_subnet_a.id
  vpc_security_group_ids = [aws_security_group.ec2_sg.id]
  associate_public_ip_address = true
  key_name                    = "jerome-aws-frankfurt"
  tags = {
    Name     = var.instance_name
    ExtraTag = local.extra_tag
  }
}

resource "aws_db_instance" "db_instance" {
  allocated_storage   = 20
  storage_type        = "gp2"
  engine              = "postgres"
  engine_version      = "14.17"
  instance_class      = "db.t3.micro"
  db_name             = "grocerymate_db"
  username            = var.db_user
  password            = var.db_pass
  db_subnet_group_name   = aws_db_subnet_group.grocery_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  skip_final_snapshot = true
}
