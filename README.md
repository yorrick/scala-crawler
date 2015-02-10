
## Build and run the container

```
docker build --tag onescience/scala-scripts .
docker run -ti --name scala-scripts --volume $PWD:/data --memory=3g --cpuset=3 onescience/scala-scripts
```

