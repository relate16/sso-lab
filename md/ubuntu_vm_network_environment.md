# Ubuntu VM 서버 네트워크 환경 구성

> 목적: AI Agent가 서버 개발/배포 작업을 수행할 때 현재 네트워크 환경과
> 제약사항을 이해하기 위한 문서

## 1. 환경 개요

현재 서버는 별도 클라우드/VPS가 아니라 **Windows Host PC의 VMware에서
실행되는 Ubuntu VM**이다.

Ubuntu VM은 VMware의 **Bridged Network**를 사용하며, 상위 네트워크의
DHCP 서버로부터 **공인 IPv4를 직접 할당**받는다.

현재 확인된 구성:

  항목                값
  ------------------- -------------------
  VM OS               Ubuntu
  VM 네트워크         VMware Bridged
  VM 인터페이스       `ens33`
  IP 할당 방식        DHCP
  현재 VM 공인 IPv4   `<dynamic-public-ip>`
  Prefix              `/25`
  Subnet Mask         `255.255.255.128`
  Gateway             `210.121.175.254`
  DNS 1               `168.126.63.1`
  DNS 2               `168.126.63.2`
  DNS Domain          `kornet`
  DDNS                DuckDNS 사용
  DDNS 갱신 위치      Ubuntu VM
  DDNS 갱신 주기      5분 (`cron`)

주의: `<dynamic-public-ip>`는 현재 DDNS가 가리키는 공인 IP이지만 **고정 IP가
아니다. DHCP에 의해 변경될 수 있다.**

따라서 애플리케이션 설정에서 가능한 경우 공인 IP를 하드코딩하지 말고
**DDNS 호스트명 사용을 우선**한다.

------------------------------------------------------------------------

## 2. 네트워크 구조

``` text
                    Internet
                       │
                       │
                 DuckDNS (DDNS)
                       │
                 현재 공인 IP
                 <dynamic-public-ip>
                       │
                       ▼
              ┌─────────────────┐
              │    Ubuntu VM    │
              │ VMware Bridged  │
              │                 │
              │ ens33           │
              │ DHCP Public IP  │
              └─────────────────┘
                       │
             ┌─────────┼─────────┐
             │         │         │
            :22       :80       :443
            SSH       HTTP      HTTPS
```

Bridged 방식이므로 외부 요청이 Windows Host의 VMware NAT 포트포워딩을
거치는 구조가 아니다.

Ubuntu VM이 공인 IP를 직접 할당받아 외부 인터넷과 통신한다.

------------------------------------------------------------------------

## 3. Ubuntu 네트워크 정보

확인 명령:

``` bash
ip addr
```

확인 당시 주요 결과:

``` text
2: ens33: <BROADCAST,MULTICAST,UP,LOWER_UP>
    altname enp2s1
    inet <dynamic-public-ip>/<dynamic-prefix>
```

라우팅 확인:

``` bash
ip route
```

확인 당시:

``` text
default via 210.121.175.254 dev ens33
210.121.175.128/25 dev ens33
```

DNS 확인:

``` bash
resolvectl status
```

확인 당시:

``` text
Current DNS Server: 168.126.63.1
DNS Servers: 168.126.63.1 168.126.63.2
DNS Domain: kornet
```

------------------------------------------------------------------------

## 4. 외부 인바운드 접속 검증

Ubuntu VM이 실제 외부 인터넷에서 접근 가능한지 테스트 완료했다.

테스트용 HTTP 서버:

``` bash
python3 -m http.server 8080
```

위 명령은 현재 디렉터리를 서비스하는 Python 기본 HTTP 서버를 모든
인터페이스의 TCP `8080` 포트에 실행한다.

로컬 테스트:

``` bash
curl http://127.0.0.1:8080
```

결과: **성공**

VM 공인 IP 테스트:

``` bash
curl http://<dynamic-public-ip>:8080
```

결과: **성공**

휴대전화에서 Wi-Fi를 끄고 모바일 데이터(LTE/5G)를 사용하여:

``` text
http://<dynamic-public-ip>:8080
```

접속 테스트 결과: **성공**

따라서 현재 네트워크에서는 외부 인터넷에서 Ubuntu VM의 공인 IP 및 열린
포트로 직접 접근할 수 있음이 확인되었다.

------------------------------------------------------------------------

## 5. DDNS를 사용하는 이유

Ubuntu VM의 공인 IP는 DHCP로 할당되므로 변경될 수 있다.

예:

``` text
현재
<dynamic-public-ip>

        ↓ DHCP 변경 가능

향후
210.121.xxx.xxx
```

따라서 DuckDNS를 사용해 고정된 호스트명이 현재 공인 IP를 가리키도록
한다.

``` text
DuckDNS hostname
       │
       ▼
현재 Ubuntu Public IPv4
       │
       ▼
Ubuntu VM
```

IP가 변경되어도 Ubuntu에서 DuckDNS API를 호출해 DNS 레코드를 갱신한다.

------------------------------------------------------------------------

## 6. DuckDNS 갱신 구성

DuckDNS 갱신용 디렉터리:

``` bash
/home/today/duckdns/
```

스크립트:

``` text
/home/today/duckdns/duck.sh
```

예시 내용:

``` bash
#!/bin/bash

curl -s "https://www.duckdns.org/update?domains=<DUCKDNS_SUBDOMAIN>&token=<DUCKDNS_TOKEN>&ip=" \
  -o /home/today/duckdns/duck.log
```

### 중요

`ip=`는 의도적으로 비워둔다.

DuckDNS가 HTTP 요청의 출발지 공인 IPv4를 확인하여 해당 주소로 DNS
레코드를 갱신한다.

따라서 다음처럼 현재 IP를 직접 넣지 않는다.

``` text
ip=<dynamic-public-ip>   # 사용하지 않음
```

그렇게 하면 DHCP로 IP가 변경되었을 때 기존 IP가 계속 등록될 수 있기
때문이다.

DuckDNS Token은 인증정보이므로 소스코드, Git 저장소, 문서 등에 실제 값을
저장하지 않는다.

------------------------------------------------------------------------

## 7. DuckDNS 자동 갱신

사용자 `today`의 crontab을 사용한다.

확인:

``` bash
crontab -l
```

등록된 작업:

``` cron
*/5 * * * * /home/today/duckdns/duck.sh >/dev/null 2>&1
```

의미:

``` text
*/5 * * * *
 ↓
5분마다 실행
```

즉 Ubuntu VM이 실행 중이면 5분마다 DuckDNS에 현재 공인 IP를 알려준다.

로그 확인:

``` bash
cat /home/today/duckdns/duck.log
```

정상 갱신 시 일반적으로:

``` text
OK
```

가 기록된다.

------------------------------------------------------------------------

## 8. 서버 개발 시 AI Agent가 지켜야 할 사항

### IP 하드코딩 금지

현재 공인 IP:

``` text
<dynamic-public-ip>
```

는 DHCP 주소이므로 영구적인 서버 주소로 가정하면 안 된다.

외부 서비스, callback URL, API endpoint 등에는 가능한 경우 **DuckDNS
호스트명**을 사용한다.

### VMware NAT 포트포워딩 불필요

현재 VM은 `Bridged` 방식이다.

따라서 다음 구조가 아니다.

``` text
Internet
  ↓
Windows Host
  ↓
VMware NAT Port Forward
  ↓
Ubuntu
```

실제 구조는:

``` text
Internet
  ↓
Ubuntu VM Public IP
```

이다.

### 서비스 포트

서비스를 외부에 공개하려면 해당 프로세스가 필요한 인터페이스에서
LISTEN해야 한다.

확인:

``` bash
sudo ss -lntp
```

특정 포트 확인:

``` bash
sudo ss -lntp | grep <PORT>
```

예를 들어 애플리케이션이 `127.0.0.1:8080`에만 바인딩되어 있으면 외부에서
직접 접근할 수 없다.

외부 공개가 필요한 서비스라면 요구사항에 따라 `0.0.0.0:<PORT>` 또는
적절한 인터페이스에 바인딩한다.

단, DB/Redis 등 외부 공개가 불필요한 서비스는 무조건 `0.0.0.0`에
노출하지 않는다.

------------------------------------------------------------------------

## 9. Ubuntu 방화벽

### 현재 상태

현재 Ubuntu 서버의 **UFW 방화벽은 비활성화(`inactive`) 상태**이다.

확인 명령:

```bash
sudo ufw status
```

현재 확인된 상태:

```text
Status: inactive
```

따라서 외부 모바일 네트워크(LTE/5G)에서 `<dynamic-public-ip>:8080`으로 접속에 성공한 것은 UFW에 8080 허용 규칙을 추가했기 때문이 아니다. 현재 UFW 자체가 비활성화되어 있어 Ubuntu 방화벽에서 해당 포트를 차단하지 않는 상태이다.

### 향후 UFW를 활성화할 경우

원격 SSH로 서버를 관리하므로 **UFW 활성화 전에 SSH 포트를 먼저 허용해야 한다.** 그렇지 않으면 원격 SSH 접속이 차단될 수 있다.

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

필요한 애플리케이션 포트를 직접 공개해야 하는 경우에만 추가한다.

```bash
sudo ufw allow <PORT>/tcp
```

필요한 규칙을 등록한 뒤 활성화한다.

```bash
sudo ufw enable
```

운영 환경에서는 필요한 포트만 허용한다. Spring Boot 등의 내부 애플리케이션 포트를 직접 인터넷에 노출하기보다 Nginx 등의 Reverse Proxy 뒤에 두는 구성을 우선 고려한다.

------------------------------------------------------------------------

## 10. Windows Host 참고 정보

Windows Host 역시 상위 네트워크에서 DHCP를 사용한다.

확인 당시:

``` text
IPv4       : 210.121.180.103
Subnet     : 255.255.255.224
Gateway    : 210.121.180.126
DHCP       : Enabled
DHCP Server: 125.141.115.26
DNS        : 168.126.63.1
             168.126.63.2
```

하지만 현재 서버 트래픽은 Host의 공인 IP를 사용하는 NAT 구조가 아니라
**Bridged VM이 별도의 공인 IP를 직접 할당받는 구조**이다.

VMware 가상 네트워크 참고:

``` text
VMnet1 (Host-only)
Host: 192.168.199.1/24

VMnet8 (NAT)
Host: 192.168.233.1/24
```

현재 서버 VM에서는 이들 대신 **Bridged Network를 사용한다.**

------------------------------------------------------------------------

## 11. 현재 네트워크 구성 요약

``` text
Server
  OS            : Ubuntu
  Hypervisor    : VMware
  Network Mode  : Bridged
  Interface     : ens33
  IP Assignment : DHCP
  Current IPv4  : <dynamic-public-ip>/<dynamic-prefix>
  Gateway       : 210.121.175.254
  DNS           : 168.126.63.1, 168.126.63.2

Inbound Test
  TCP 8080      : External LTE/5G access verified

DDNS
  Provider      : DuckDNS
  Updater       : /home/today/duckdns/duck.sh
  Schedule      : every 5 minutes via cron

Firewall
  UFW           : inactive (currently disabled)

Important
  - Public IPv4 is NOT static.
  - Do not hardcode the current DHCP public IP as a permanent endpoint.
  - Prefer the configured DuckDNS hostname.
  - No VMware NAT port forwarding is required.
  - Expose only necessary ports.
```

## 12. AI Agent 작업 전 확인 권장 명령

서버 작업을 시작하기 전에 다음을 확인한다.

``` bash
# 현재 IP
ip addr show ens33

# Default gateway
ip route

# DNS
resolvectl status

# 현재 LISTEN 포트
sudo ss -lntp

# Firewall
sudo ufw status

# DDNS cron
crontab -l

# DuckDNS 최근 결과
cat /home/today/duckdns/duck.log
```

현재 IP와 DuckDNS DNS 레코드가 일치하는지도 확인할 수 있다.

``` bash
nslookup <DUCKDNS_HOSTNAME>
```

**AI Agent는 문서에 기록된 과거 주소보다 위 명령으로 확인한 현재
상태를 우선한다.**
