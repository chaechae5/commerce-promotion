# 1단계: Gradle을 사용하여 애플리케이션 빌드
# 'openjdk:17-jdk-slim' 대신 'eclipse-temurin:17-jdk-jammy' 사용
FROM eclipse-temurin:17-jdk-jammy as builder
WORKDIR /workspace/app

# 빌드에 필요한 파일들만 먼저 복사하여 Gradle 캐시 활용
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
# 의존성 먼저 다운로드
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사 후 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실제 실행을 위한 작은 이미지 생성
# 'openjdk:17-jre-slim' 대신 'eclipse-temurin:17-jre-jammy' 사용
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 1단계(builder)에서 만들어진 .jar 파일만 복사
COPY --from=builder /workspace/app/build/libs/*.jar app.jar

# 컨테이너 실행 시 이 명령어를 실행
ENTRYPOINT ["java", "-jar", "app.jar"]