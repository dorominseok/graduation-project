# 백엔드 운영 이미지. 빌드 단계에서 jar를 만들고, 실행 단계에는 JRE와 jar만 남긴다.
# 빌드는 deploy/compose.yml이 서버에서 직접 한다(구축 기록 5.1).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성만 먼저 받아 둔다. 소스만 바뀐 재빌드는 이 층을 다시 쓰므로 빨라진다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# 테스트는 CI가 PR마다 돌린다. 서버에서 다시 돌리면 2GB 메모리로는 빌드가 버겁다
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home app
# bootJar 결과물만. 같은 폴더의 *-plain.jar는 실행할 수 없는 jar다
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER app

EXPOSE 8080
# 힙 상한은 compose의 JAVA_OPTS로 준다. 한 서버에 DB·웹서버와 같이 산다
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
