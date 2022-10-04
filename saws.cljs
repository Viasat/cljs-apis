#!/usr/bin/env nbb

(ns saws
  (:require [viasat.util :refer [parse-opts Eprintln Epprint]]
            [viasat.apis.saws :refer [usage-general usage-options
                                      service-aliases arg->keyword run]]))

(def usage-aliases "
Usage:
  saws ec2 list [options]
  saws ec2 ips [options]
  saws ec2 console [options] <instance-id>

  saws vpc list [options]

  saws <cf|cfn> list-all [options]
  saws <cf|cfn> list [options]
  saws <cf|cfn> show [options] <stack>
  saws <cf|cfn> params [options] <stack>
  saws <cf|cfn> outputs [options] <stack>
  saws <cf|cfn> events [options] <stack>
  saws <cf|cfn> resources [options] <stack>
  saws <cf|cfn> status [options] <stack>
  saws <cf|cfn> delete [options] <stack>

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
  saws ecr list [options] <repo>")

(def command-schemas
  {[:ec2 :list]      {:command "DescribeInstances"
                      :fields [:InstanceId
                               [:Name [:Tags [:Key "Name" :Value]]]
                               [:State [:State :Name]]
                               :PrivateIpAddress
                               :KeyName
                               :InstanceType]
                      :extract [:Reservations :Instances]}
   [:ec2 :ips]       {:command "DescribeInstances"
                      :fields [:InstanceId :PrivateIpAddress]
                      :extract [:Reservations :Instances]}
   [:ec2 :console]   {:command "GetConsoleOutput"
                      :extract [:Output {:action :decode-base64}]}
   [:vpc :list]      {:command "DescribeVpcs"
                      :fields [:VpcId
                               [:Name [:Tags [:Key "Name" :Value]]]
                               :CidrBlock
                               :State
                               :OwnerId
                               :DhcpOptionsId
                               :InstanceTenancy
                               :Ipv6CidrBlockAssociationSet
                               :IsDefault]}

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
                      :sort :imagePushedAt}})

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
