#!/bin/bash
svc_list="assurance auth config consign-price consign contacts delivery food food-delivery inside-payment notification order-other order payment price route security station-food station ticket-office train-food train travel travel2 user voucher wait-order"

secret_yaml="deployment/kubernetes-manifests/quickstart-k8s/yamls/secret.yaml"
dp_sample_yaml="deployment/kubernetes-manifests/quickstart-k8s/yamls/deploy.yaml.sample"
sw_dp_sample_yaml="deployment/kubernetes-manifests/quickstart-k8s/yamls/sw_deploy.yaml.sample"

dp_yaml="deployment/kubernetes-manifests/quickstart-k8s/yamls/deploy.yaml"
sw_dp_yaml="deployment/kubernetes-manifests/quickstart-k8s/yamls/sw_deploy.yaml"


function gen_secret_for_tt {
  s="$1"
  name="ts-$s-mysql"
  hostVal="$2"
  userVal="$3"
  passVal="$4"
  dbVal="$5"

  prefix=`echo "${s}-mysql-" | tr '-' '_' | tr a-z A-Z`
  host=$prefix"HOST"
  port=$prefix"PORT"
  database=$prefix"DATABASE"
  user=$prefix"USER"
  pwd=$prefix"PASSWORD"

  cat>>$secret_yaml<<EOF
apiVersion: v1
kind: Secret
metadata:
  name: $name
type: Opaque
stringData:
  $host: "$hostVal"
  $port: "3306"
  $database: "$dbVal"
  $user: "$userVal"
  $pwd: "$passVal"
---
EOF
}

function gen_secret_for_services {
  mysqlUser="$1"
  mysqlPassword="$2"
  mysqlDatabase="$3"
  mysqlHost=""
  useOneHost=0

  if [ $# == 4 ]; then
    mysqlHost="$4"
    useOneHost=1
  fi
  rm $secret_yaml > /dev/null 2>&1
  touch $secret_yaml
  for s in $svc_list
  do
    if [ useOneHost == 0 ]; then
      mysqlHost="ts-$s-mysql-leader"
    fi
    gen_secret_for_tt $s $mysqlHost $mysqlUser $mysqlPassword $mysqlDatabase
  done
}

function update_tt_dp_cm {
  nacosCM="$1"
  rabbitmqCM="$2"

  echo "Updating deploy.yaml with nacos=${nacosCM} and rabbitmq=${rabbitmqCM}"
  echo "Sample file: $dp_sample_yaml"
  echo "Target file: $dp_yaml"
  if [ ! -f "$dp_sample_yaml" ]; then
    echo "Error: $dp_sample_yaml does not exist"
    return 1
  fi

  cp $dp_sample_yaml $dp_yaml

  if [ ! -f "$dp_yaml" ]; then
    echo "Error: Failed to create $dp_yaml"
    return 1
  fi

  sed -i.bak "s/nacos/${nacosCM}/g" $dp_yaml && rm ${dp_yaml}.bak
  sed -i.bak "s/rabbitmq/${rabbitmqCM}/g" $dp_yaml && rm ${dp_yaml}.bak


}

function update_tt_sw_dp_cm {
  nacosCM="$1"
  rabbitmqCM="$2"
  echo "Current directory: $(pwd)"
  echo "Sample file path: $sw_dp_sample_yaml"
  echo "Target file path: $sw_dp_yaml"

  echo "Copying sample file..."
  cp $sw_dp_sample_yaml $sw_dp_yaml
  if [ -f "$sw_dp_yaml" ]; then
    echo "File copied successfully"
  else
    echo "Failed to copy file"
    return 1
  fi

  echo "Updating file with nacos=${nacosCM} and rabbitmq=${rabbitmqCM}"

  # Add error checking for sed commands
  if sed -i "s/nacos/${nacosCM}/g" $sw_dp_yaml; then
    echo "Nacos replacement successful"
  else
    echo "Nacos replacement failed"
    return 1
  fi

  if sed -i "s/rabbitmq/${rabbitmqCM}/g" $sw_dp_yaml; then
    echo "RabbitMQ replacement successful"
  else
    echo "RabbitMQ replacement failed"
    return 1
  fi

  echo "File update complete"

  # Check if any changes were made
  if diff $sw_dp_sample_yaml $sw_dp_yaml > /dev/null; then
    echo "Warning: No changes were made to the file"
  else
    echo "Changes were successfully made to the file"
  fi
}

