# Selenium Grid 监控指南

## 一、实时查看浏览器操作（VNC）

### 方法1: 浏览器访问（noVNC）- 最简单 ⭐

Selenium 4.18.1+ 内置noVNC服务器，无需安装任何客户端：

```bash
# Chrome Node 1
http://localhost:7900

# Chrome Node 2  
http://localhost:7901

# Chrome Node 3
http://localhost:7902

# Chrome Node 4
http://localhost:7903
```

**密码**: `secret`（默认）

### 方法2: VNC客户端访问（更流畅）

使用专业VNC客户端获得更好的性能：

```bash
# macOS推荐
brew install --cask vnc-viewer

# 连接地址
vnc://localhost:5900  # Chrome Node 1
vnc://localhost:5901  # Chrome Node 2
vnc://localhost:5902  # Chrome Node 3
vnc://localhost:5903  # Chrome Node 4
```

**密码**: `secret`（默认）

## 二、查看容器日志

### 实时查看某个Node的日志
```bash
# 查看Chrome Node 1的日志
docker logs -f chrome-node-1

# 查看最近100行日志
docker logs --tail 100 chrome-node-1

# 查看所有节点日志
docker-compose -f docker-compose-selenium.yml logs -f
```

### 查看Hub日志
```bash
docker logs -f selenium-hub
```

## 三、监控Grid状态

### 1. Web UI界面
```bash
# Grid Console - 查看所有节点状态
http://localhost:4444/ui

# Grid Status API - JSON格式
curl http://localhost:4444/status | jq
```

### 2. 查看会话信息
```bash
# 查看当前活跃的会话
curl http://localhost:4444/status | jq '.value.nodes[].slots'

# 查看节点详情
curl http://localhost:4444/status | jq '.value.nodes[] | {id, uri, maxSessions, slots}'
```

## 四、视频录制（可选）

如需录制测试视频，添加视频服务：

```yaml
# 在docker-compose-selenium.yml中添加
video:
  image: selenium/video:ffmpeg-6.1-20240402
  volumes:
    - ./videos:/videos
  depends_on:
    - chrome-node-1
  environment:
    - DISPLAY_CONTAINER_NAME=chrome-node-1
    - FILE_NAME=chrome-node-1.mp4
```

## 五、常用调试命令

### 检查Node是否正常注册
```bash
# 查看已注册的节点数量
curl -s http://localhost:4444/status | jq '.value.nodes | length'

# 查看每个节点的状态
curl -s http://localhost:4444/status | jq '.value.nodes[] | {uri, availability}'
```

### 进入容器内部调试
```bash
# 进入Chrome Node 1容器
docker exec -it chrome-node-1 /bin/bash

# 查看浏览器版本
docker exec chrome-node-1 google-chrome --version

# 查看ChromeDriver版本
docker exec chrome-node-1 chromedriver --version
```

### 重启特定Node
```bash
# 重启单个节点
docker restart chrome-node-1

# 重启所有节点（不影响Hub）
docker restart chrome-node-1 chrome-node-2 chrome-node-3 chrome-node-4
```

## 六、性能监控

### 查看资源使用情况
```bash
# 查看所有容器资源占用
docker stats

# 只查看Selenium相关容器
docker stats selenium-hub chrome-node-1 chrome-node-2 chrome-node-3 chrome-node-4
```

### 监控会话并发数
```bash
# 实时监控正在运行的会话数
watch -n 1 'curl -s http://localhost:4444/status | jq ".value.nodes[].slots[] | select(.session != null) | .session.sessionId"'
```

## 七、故障排查

### Node无法连接到Hub
```bash
# 检查网络连接
docker exec chrome-node-1 ping -c 3 selenium-hub

# 检查端口是否可达
docker exec chrome-node-1 nc -zv selenium-hub 4442
docker exec chrome-node-1 nc -zv selenium-hub 4443
```

### 会话卡住不释放
```bash
# 查看所有活跃会话
curl -s http://localhost:4444/status | jq '.value.nodes[].slots[] | select(.session != null)'

# 强制重启Node释放会话
docker restart chrome-node-1
```

### VNC无法连接
```bash
# 检查端口是否开放
netstat -an | grep 7900

# 检查容器内VNC服务
docker exec chrome-node-1 ps aux | grep vnc
```

## 八、最佳实践

### 开发环境配置
```bash
# 1. 启动Grid（后台运行）
docker-compose -f docker-compose-selenium.yml up -d

# 2. 打开浏览器监控
open http://localhost:4444/ui      # Grid Console
open http://localhost:7900         # Node 1 VNC

# 3. 运行测试
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. 实时查看日志
docker logs -f chrome-node-1
```

### 生产环境监控
- 使用 Prometheus + Grafana 收集Grid指标
- 配置日志聚合（ELK/Loki）
- 设置告警（节点离线、会话堆积）

## 九、快速命令参考

```bash
# 重启Grid
docker-compose -f docker-compose-selenium.yml restart

# 查看Grid状态
curl http://localhost:4444/status | jq '.value.ready'

# 打开VNC查看Node 1
open http://localhost:7900

# 查看实时日志
docker logs -f chrome-node-1

# 查看资源使用
docker stats --no-stream

# 清理所有容器
docker-compose -f docker-compose-selenium.yml down
```

## 十、VNC快捷操作

### 浏览器noVNC界面
- **全屏**: 点击左侧工具栏的全屏图标
- **剪贴板**: 点击左侧工具栏的剪贴板图标，可粘贴文本到容器
- **快捷键**: 通过左侧工具栏的键盘图标发送特殊按键（Ctrl+Alt+Del等）

### VNC客户端
- **刷新**: 按 `Cmd+R` 刷新画面
- **全屏**: 按 `Cmd+F` 切换全屏
- **截图**: 直接使用macOS截图快捷键 `Cmd+Shift+4`

## 参考资料
- [Selenium Grid官方文档](https://www.selenium.dev/documentation/grid/)
- [Docker Selenium GitHub](https://github.com/SeleniumHQ/docker-selenium)
