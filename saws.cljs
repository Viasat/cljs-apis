#!/usr/bin/env nbb

;; Copyright (c) 2024, Viasat, Inc
;; Licensed under EPL 2.0

(ns saws
  (:require [viasat.util :refer [parse-opts Eprintln Epprint]]
            [viasat.apis.saws :refer [usage-general usage-options
                                      service-aliases arg->keyword run]]))

(def usage-aliases "
Usage:
  saws iam users [options]
  saws iam user-groups [options] <user-name>
  saws iam user-inline-policies [options] <user-name>
  saws iam user-attached-policies [options] <user-name>

  saws iam groups [options]
  saws iam group-inline-policies [options] <group-name>
  saws iam group-attached-policies [options] <group-name>

  saws iam roles [options]
  saws iam role-inline-policies [options] <role-name>
  saws iam role-attached-policies [options] <role-name>

  saws iam policies [options]
  saws iam policy-doc [options] <policy-arn> <policy-version>
  saws iam policy-users [options] <policy-arn>
  saws iam policy-groups [options] <policy-arn>
  saws iam policy-roles [options] <policy-arn>

  saws ec2 list [options]
  saws ec2 ips [options]
  saws ec2 console [options] <instance-id>
  saws ec2 keys [options]
  saws ec2 sgs [options]
  saws ec2 sg-rules [options]
  saws ec2 enis [options]
  saws ec2 amis [options]
  saws ec2 delete-eni [options] <eni-id>

  saws vpc vpcs [options]
  saws vpc subnets [options]

  saws <cf|cfn> list-all [options]
  saws <cf|cfn> list [options]
  saws <cf|cfn> show [options] <stack>
  saws <cf|cfn> params [options] <stack>
  saws <cf|cfn> outputs [options] <stack>
  saws <cf|cfn> events [options] <stack>
  saws <cf|cfn> resources [options] <stack>
  saws <cf|cfn> status [options] <stack>
  saws <cf|cfn> delete [options] <stack>

  saws sc portfolios [options]
  saws sc products [options]
  saws sc versions [options] <product-id>
  saws sc delete-version [options] <product-id> <artifact-version-id>
  saws sc provisioned [options]
  saws sc provision [options]       <prov-name> <product> <artifact-version>
  saws sc provision [options] --ids <prov-name> <product-id> <artifact-version-id>
  saws sc terminate [options] <prov-name>
  saws sc terminate [options] --ids <prov-id>

  saws <db|dynamodb> list [options]
  saws <db|dynamodb> scan [options] <table>

  saws ecr repos [options]
  saws ecr tags [options] <repo>
  saws ecr list [options] <repo>

  saws ssm params [options]
  saws ssm delete-param [options] <name>")

(def command-schemas
  {[:iam :users]     {:command "ListUsers"
                      :fields [:UserId :UserName :Arn :CreateDate
                               :PasswordLastUsed] }
   [:iam :user-groups] {:command "ListGroupsForUser"}
   [:iam :user-inline-policies] {:command "ListUserPolicies"}
   [:iam :user-attached-policies] {:command "ListAttachedUserPolicies"}

   [:iam :groups]    {:command "ListGroups"
                      :fields [:GroupId  :GroupName :Arn :CreateDate]}
   [:iam :group-inline-policies] {:command "ListGroupPolicies"}
   [:iam :group-attached-policies] {:command "ListAttachedGroupPolicies"}

   [:iam :roles]     {:command "ListRoles"
                      :fields [:RoleId :RoleName :Description :Arn :CreateDate
                                :MaxSessionDuration :Tags :RoleLastUsed]}
   [:iam :role-inline-policies] {:command "ListRolePolicies"}
   [:iam :role-attached-policies] {:command "ListAttachedRolePolicies"}

   [:iam :policies]  {:command "ListPolicies"
                      :fields [:PolicyId :PolicyName :Description
                               :Arn :DefaultVersionId
                               :AttachmentCount :IsAttachable
                               :CreateDate :UpdateDate]}
   [:iam :policy-doc] {:command "GetPolicyVersion"
                        :extract [:PolicyVersion :Document {:action :decode-url}] }
   [:iam :policy-users]  {:command "ListEntitiesForPolicy"
                           :extract [:PolicyUsers]}
   [:iam :policy-groups]  {:command "ListEntitiesForPolicy"
                           :extract [:PolicyGroups]}
   [:iam :policy-roles]  {:command "ListEntitiesForPolicy"
                           :extract [:PolicyRoles]}

   [:ec2 :list]      {:command "DescribeInstances"
                      :fields [:InstanceId
                               [:Name [:Tags [:Key "Name" :Value]]]
                               [:State [:State :Name]]
                               :PrivateIpAddress
                               :KeyName
                               :InstanceType
                               :LaunchTime]
                      :extract [:Reservations :Instances]}
   [:ec2 :ips]       {:command "DescribeInstances"
                      :fields [:InstanceId :PrivateIpAddress]
                      :extract [:Reservations :Instances]}
   [:ec2 :console]   {:command "GetConsoleOutput"
                      :extract [:Output {:action :decode-base64}]}
   [:ec2 :keys]      {:command "DescribeKeyPairs"}
   [:ec2 :sgs]       {:command "DescribeSecurityGroups"}
   [:ec2 :sg-rules]  {:command "DescribeSecurityGroupRules"
                      :fields [:GroupId
                               ;;:SecurityGroupRuleId
                               :IsEgress
                               :IpProtocol
                               :CidrIpv4
                               ;;:CidrIpv6
                               :FromPort
                               :ToPort
                               :Description]
                      :sort :GroupId}
   [:ec2 :enis]      {:command "DescribeNetworkInterfaces"
                      :fields [:NetworkInterfaceId
                               :MacAddress
                               :PrivateIpAddress
                               :Status
                               :VpcId
                               :SubnetId
                               :AvailabilityZone
                               [:InstanceId [:Attachment :InstanceId]]
                               [:Group [:Groups 0 :GroupId]]
                               :Description]}
   [:ec2 :amis]      {:command "DescribeImages"
                      :parameters {:Owners ["self"]}
                      :fields [:ImageId
                               :Name
                               :Description
                               :CreationDate
                               :OwnerId
                               :State
                               :RootDeviceName
                               :PlatformDetails
                               [:Base_AMI_ID [:Tags [:Key "Base_AMI_ID" :Value]]]
                               [:Base_AMI_Name [:Tags [:Key "Base_AMI_Name" :Value]]]]
                      :sort :CreationDate}
   [:ec2 :delete-eni] {:command "DeleteNetworkInterface"
                       :extract [{:action :raw}]}

   [:vpc :vpcs]      {:command "DescribeVpcs"
                      :fields [:VpcId
                               [:Name [:Tags [:Key "Name" :Value]]]
                               :CidrBlock
                               :State
                               :OwnerId
                               :DhcpOptionsId
                               :IsDefault]}
   [:vpc :subnets]   {:command "DescribeSubnets"
                      :fields [:SubnetId
                               [:Name [:Tags [:Key "Name" :Value]]]
                               :VpcId
                               :CidrBlock
                               :State
                               :OwnerId
                               :AvailableIpAddressCount
                               :AvailabilityZone
                               :AvailabilityZoneId
                               :DefaultForAz]}

   [:cfn :list-all]  {:command "ListStacks"
                      :fields [:StackId :StackStatus :CreationTime
                               :LastUpdatedTime :DeletionTime]}
   [:cfn :list]      {:command "DescribeStacks"
                      :fields [:StackName :StackStatus :CreationTime
                               :LastUpdatedTime :DeletionTime]}
   [:cfn :show]      {:command "DescribeStacks"
                      :fields [:StackName :StackStatus :CreationTime
                               :LastUpdatedTime]}
   [:cfn :params]    {:command "DescribeStacks"
                      :fields [:ParameterKey :ParameterValue]
                      :extract [:Stacks :Parameters]}
   [:cfn :outputs]   {:command "DescribeStacks"
                      :fields [:OutputKey :OutputValue :Description]
                      :extract [:Stacks :Outputs]}
   [:cfn :resources] {:command "DescribeStackResources"
                      :fields [:LogicalResourceId :ResourceType
                               :ResourceStatus :ResourceStatusReason
                               :Timestamp]}
   [:cfn :events]    {:command "DescribeStackEvents"
                      :fields [:Timestamp :LogicalResourceId
                               :ResourceType :ResourceStatus
                               :ResourceStatusReason]}
   [:cfn :status]    {:command "DescribeStackEvents"
                      :fields [:LogicalResourceId
                               :ResourceType :ResourceStatus
                               :ResourceStatusReason :Timestamp]
                      :uniq :LogicalResourceId
                      :rsort :Timestamp}
   [:cfn :deploy]    {:command "DeployStack"}
   [:cfn :delete]    {:command "DeleteStack"
                      :extract [{:action :raw}]}

   [:sc  :portfolios] {:command "ListPortfolios"
                       :fields [:Id :DisplayName :Description :CreatedTime :ProviderName :ARN]}
   [:sc  :products]  {:command "SearchProducts"
                      :fields [:ProductId :Name :ShortDescription]
                      :extract [:ProductViewSummaries]}
   [:sc  :versions]  {:command "ListProvisioningArtifacts"
                      :fields [:Id :Name :Description :CreatedTime :Active]}
   [:sc  :delete-version] {:command "DeleteProvisioningArtifact"}
   [:sc  :provisioned] {:command "SearchProvisionedProducts"
                        :fields [:Id
                                 :Name
                                 :CreatedTime
                                 :ProvisioningArtifactName
                                 :Status
                                 :StatusMessage
                                 :PhysicalId
                                 :ProductId
                                 :ProductName]
                        :sort :CreatedTime}
   [:sc  :provision] {:command "ProvisionProduct"
                      :extract [{:action :raw}]}
   [:sc  :terminate] {:command "TerminateProvisionedProduct"
                      :extract [{:action :raw}]}

   [:dynamodb :list] {:command "ListTables"
                      :extract [:TableNames]}
   [:dynamodb :scan] {:command "Scan"
                      :extract [:Items {:action :raw}]}
   [:ecr :repos]     {:command "DescribeRepositories"
                      :fields [:repositoryName
                               :imageTagMutability
                               :createdAt
                               :repositoryUri]
                      :field-max 100}
   [:ecr :tags]      {:command "ListImages"
                      :extract [:imageIds :imageTag {:action :remove-nulls}]}
   [:ecr :list]      {:command "DescribeImages"
                      :fields [:imageDigest
                               :imagePushedAt
                               :imageSizeInBytes
                               :lastRecordedPullTime
                               :imageTags]
                      :sort :imagePushedAt}
   [:ssm :params]    {:command "DescribeParameters"
                      :fields [:Name
                               :DataType
                               :Description
                               :Type
                               :Tier
                               :Version
                               :LastModifiedDate
                               :LastModifiedUser]}
   [:ssm :delete-param] {:command "DeleteParameter"
                         :extract [{:action :raw}]}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [argv *command-line-args*
      cfg (parse-opts (str usage-aliases usage-options)
                      argv {:laxPlacement true})
      cfg-gen (parse-opts (str usage-general usage-options) argv
                          {:laxPlacement true})
      {:keys [service command]} cfg-gen
      [service command] (map arg->keyword [service command])
      service (get service-aliases (name service) service)
      command-schema (get command-schemas [service command])
      _ (when (empty? command-schema)
          (println "Error: unknown alias combo" [service command])
          (js/process.exit 2))
      _ (when (empty? cfg) (js/process.exit 2))]
  (run cfg service command command-schema))
