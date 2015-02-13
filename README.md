
## Description

build.sbt file is there only for IDE support.



## Build and run the container

```
docker build --tag onescience/scala-scripts . && docker push onescience/scala-scripts
sudo docker pull onescience/scala-scripts && sudo docker run -d --volume $PWD:/data --memory=3g onescience/scala-scripts /bin/bash -c 'cat /scripts/repo-urls.txt | /scripts/fetch.scala > /data/repo-result.csv'
```

## Launch fetch

```
cat repo-urls.txt | ./fetch.scala > repo-result.csv
```

