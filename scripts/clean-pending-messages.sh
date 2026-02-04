#!/bin/bash
# 重新投递 Redis Stream 中超时的 Pending 消息
# 使用 XCLAIM 将消息均匀分配给所有活跃的 consumer

STREAM_KEY="xhs:stream:crawl"
GROUP_NAME="crawl-workers"
TIMEOUT_MS=300000  # 5分钟（超过这个时间认为消费者已死）

echo "🔍 检查 Pending 消息..."
PENDING_COUNT=$(redis-cli XPENDING $STREAM_KEY $GROUP_NAME | head -1 | awk '{print $1}')
echo "📊 当前 Pending 消息数: $PENDING_COUNT"

if [ "$PENDING_COUNT" = "0" ]; then
    echo "✅ 没有 Pending 消息需要重新投递"
    exit 0
fi

echo "🔍 查找活跃的 consumer..."
# 获取所有 idle 时间小于 30 秒的活跃 consumer
ACTIVE_CONSUMERS=()
while IFS= read -r consumer; do
    ACTIVE_CONSUMERS+=("$consumer")
done < <(redis-cli XINFO CONSUMERS $STREAM_KEY $GROUP_NAME | \
    awk '/^name$/{getline; name=$1} /^idle$/{getline; idle=$1; if(idle<30000) print name}')

CONSUMER_COUNT=${#ACTIVE_CONSUMERS[@]}

if [ "$CONSUMER_COUNT" -eq 0 ]; then
    echo "❌ 没有找到活跃的 consumer，请确保应用正在运行"
    exit 1
fi

echo "✅ 找到 $CONSUMER_COUNT 个活跃 consumer:"
for consumer in "${ACTIVE_CONSUMERS[@]}"; do
    echo "  - $consumer"
done

echo "🔄 开始重新投递超时消息（轮询分配）..."

CLAIMED=0
FAILED=0
CONSUMER_INDEX=0

# 获取所有 Pending 消息（最多处理 2000 条）
redis-cli XPENDING $STREAM_KEY $GROUP_NAME - + 2000 | \
while IFS= read -r line1; do
    read -r line2
    read -r line3
    read -r line4
    
    MESSAGE_ID=$(echo "$line1" | tr -d '")' | awk '{print $1}')
    OLD_CONSUMER=$(echo "$line2" | tr -d '")' | awk '{print $1}')
    IDLE_TIME=$(echo "$line3" | tr -d '")' | awk '{print $1}')
    
    # 跳过不是消息 ID 的行
    if [[ ! "$MESSAGE_ID" =~ ^[0-9]+-[0-9]+$ ]]; then
        continue
    fi
    
    # 只处理超时的消息
    if [ "$IDLE_TIME" -gt "$TIMEOUT_MS" ]; then
        # 轮询选择目标 consumer
        TARGET_CONSUMER=${ACTIVE_CONSUMERS[$CONSUMER_INDEX]}
        CONSUMER_INDEX=$(( (CONSUMER_INDEX + 1) % CONSUMER_COUNT ))
        
        # 使用 XCLAIM 转移消息
        RESULT=$(redis-cli XCLAIM $STREAM_KEY $GROUP_NAME $TARGET_CONSUMER 0 $MESSAGE_ID IDLE 0 2>&1)
        
        if [[ $RESULT == *"(empty"* ]] || [ -z "$RESULT" ]; then
            echo "  ❌ 认领失败: $MESSAGE_ID"
            FAILED=$((FAILED + 1))
        else
            CLAIMED=$((CLAIMED + 1))
            if [ $((CLAIMED % 100)) -eq 0 ]; then
                echo "  ✅ 已转移 $CLAIMED 条消息..."
            fi
        fi
    fi
done

echo ""
echo "🎉 重新投递完成！"
echo "📊 统计:"
echo "  ✅ 成功转移: $CLAIMED 条"
echo "  ❌ 失败: $FAILED 条"
echo "  📦 当前 Pending:"
redis-cli XPENDING $STREAM_KEY $GROUP_NAME
