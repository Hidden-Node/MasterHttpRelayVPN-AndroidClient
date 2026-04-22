package mobilebridge

import (
    "fmt"
    "sync"

    "github.com/xjasonlyu/tun2socks/v2/engine"
)

var (
    mu      sync.Mutex
    started bool
)

func StartTun(fd int, proxyAddr string, mtu int) error {
    mu.Lock()
    defer mu.Unlock()

    if started {
        return nil
    }
    if fd <= 0 {
        return fmt.Errorf("invalid tun fd: %d", fd)
    }
    if proxyAddr == "" {
        return fmt.Errorf("proxy address is empty")
    }
    if mtu <= 0 {
        mtu = 1500
    }

    key := &engine.Key{
        Proxy:  "socks5://" + proxyAddr,
        Device: fmt.Sprintf("fd://%d", fd),
        MTU:    mtu,
    }

    engine.Insert(key)
    engine.Start()
    started = true
    return nil
}

func StopTun() {
    mu.Lock()
    defer mu.Unlock()

    if !started {
        return
    }
    engine.Stop()
    started = false
}

func IsTunRunning() bool {
    mu.Lock()
    defer mu.Unlock()
    return started
}
