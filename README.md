# <img height="50" src="https://github.com/redacted-text/RedProxy/blob/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp?raw=true"> RedProxy

Simple system global proxy working via [redsocks2](https://github.com/zcotape/redsocks2). **Requires root.** Tested with Magisk.

### Proxy formats

App can use proxy string in next formats:

```txt
socks5://host:port
socks5://login:password@host:port
```

### Broadcast control

You can control RedProxy via adb or android broadcasts.

```shell
adb shell am broadcast -a "net.redproxy.SET_PROXY" -n net.redproxy/.MainReceiver --es proxy "socks5://login:password@host:port"
```

```shell
adb shell am broadcast -a "net.redproxy.SET_PROXY" -n net.redproxy/.MainReceiver --es proxy "null"
```

### Formats to be supported

```
http://host:port
http://login:password@host:port
https://host:port
https://login:password@host:port
```