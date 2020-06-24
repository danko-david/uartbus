FROM debian:buster AS uartbus_base

RUN apt-get update
RUN apt-get install -y git gcc openjdk-11-jdk maven socat gcc-avr arduino libpcre3-dev libpth-dev


FROM uartbus_base
COPY .git /home/.git
WORKDIR /home
RUN git reset --hard
RUN ./scripts/continous_integration/ci_automated_cycle.sh
