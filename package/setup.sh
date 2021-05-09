#!/bin/sh

echo -e "\033[36m ============================ \033[0m"
echo -e "\033[36m      telegram index bot      \033[0m"
echo -e "\033[36m ============================ \033[0m"

curl -O https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-7.12.0-linux-x86_64.tar.gz
curl -LJO https://github.com/dididi-upcar/elasticsearch-analysis-ik/tarball/v7.12.0-commit


tar -xzvf elasticsearch-7.12.0-linux-x86_64.tar.gz
tar -xzvf dididi-upcar-elasticsearch-analysis-ik-v7.12.0-commit-0-g614ee3a.tar.gz -C elasticsearch-7.12.0/plugins/


rm dididi-upcar-elasticsearch-analysis-ik-v7.12.0-commit-0-g614ee3a.tar.gz elasticsearch-7.12.0-linux-x86_64.tar.gz 


echo -e "\033[36m finish \033[0m"
echo -e "press any key to continue..."
read input
echo -e "bye."