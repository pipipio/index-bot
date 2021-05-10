#!/bin/sh

user="the user name"

workspace=$(dirname $(readlink -f $0))
workspace_elastic="$workspace/elasticsearch-7.12.0"

java_home="$workspace/elasticsearch-7.12.0/jdk/bin/java"
elastic_home="$workspace_elastic/bin/elasticsearch"
bot_home="$workspace/telegram-index-bot-2.0.0.jar"

service_name="tg-se-index-bot"
``
elastic_port="9200"


if [ ! -d $workspace_elastic ]; then
    curl -O https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-7.12.0-linux-x86_64.tar.gz
    curl -LJO https://github.com/dididi-upcar/elasticsearch-analysis-ik/tarball/v7.12.0-commit
    tar -xzvf elasticsearch-7.12.0-linux-x86_64.tar.gz
    tar -xzvf dididi-upcar-elasticsearch-analysis-ik-v7.12.0-commit-0-g614ee3a.tar.gz -C elasticsearch-7.12.0/plugins/
    rm dididi-upcar-elasticsearch-analysis-ik-v7.12.0-commit-0-g614ee3a.tar.gz elasticsearch-7.12.0-linux-x86_64.tar.gz 
fi

if [[ "$1" == "help" || "$1" == "" ]]; then
    echo -e "\033[36m ============================ \033[0m"
    echo -e "\033[36m      telegram index bot      \033[0m"
    echo -e "\033[36m ============================ \033[0m"
    echo -e "\033[32m init \033[0m: Registration the index-bot service"
    echo -e "\033[32m start/restart/stop \033[0m: Operate the index-bot service"
    echo -e "\033[32m enable/disable \033[0m: Manage automatically service"
    echo -e "\033[32m upgrade \033[0m: Update project & rebuild & restart index-bot"
    echo -e "\033[32m log \033[0m: Show log"
    echo -e "\033[36m =========================== \033[0m"
    echo
elif [ "$1" == "install" ]; then
    echo "create service"
    cat >/etc/systemd/system/$service_name.service <<EOF
[Unit]
Description=$service_name
After=network.target
Wants=network.target
[Service]
User=$user
# Group=users
Type=simple
WorkingDirectory=$(readlink -e ./)
ExecStart=/bin/bash $0 run
Restart=on-failure
RestartPreventExitStatus=100
[Install]
WantedBy=multi-user.target
EOF

    sudo systemctl daemon-reload
elif [ "$1" == "remove" ]; then
    sudo systemctl stop $service_name
    sudo systemctl disable $service_name
    rm -f /etc/systemd/system/$service_name.service 
    rm -f /etc/systemd/system/$service_name 
    sudo systemctl daemon-reload
    sudo systemctl reset-failed
elif [ "$1" == "run" ]; then
    $elastic_home -d -p pid &
    printf 'Waiting for the elasticsearch to start to complete '
    until $(curl --output /dev/null --silent --head --fail http://localhost:$elastic_port); do
        printf '.'
        sleep 3
    done
    $java_home -jar $bot_home
elif [[ "$1" == "start" || "$1" == "restart" || "$1" == "stop" || "$1" == "status" || "$1" == "enable" || "$1" == "disable" ]]; then
    sudo systemctl "$1" $service_name
    echo "finish"
elif [ "$1" == "log" ]; then
  journalctl -u $service_name -o short --no-hostname -f -n 40
elif [ "$1" == "logs" ]; then
  exec journalctl -u $service_name -o short --no-hostname --no-tail -e
else
  echo "$1: command not found"
fi
