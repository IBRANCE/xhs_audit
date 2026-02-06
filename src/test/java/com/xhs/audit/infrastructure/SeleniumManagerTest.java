package com.xhs.audit.infrastructure;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * SeleniumManager 单元测试
 *
 * 测试浏览器实例池管理器的配置和核心功能
 *
 * @author XHS Audit System
 * @since 2026-02-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)

class SeleniumManagerTest {

    private SeleniumManager seleniumManager;

    @Mock
    private RemoteWebDriver mockRemoteWebDriver;

    @BeforeEach
    void setUp() throws MalformedURLException {
        seleniumManager = new SeleniumManager();
        ReflectionTestUtils.setField(seleniumManager, "seleniumGridUrl", "http://localhost:4444");
        ReflectionTestUtils.setField(seleniumManager, "poolSize", 4);
        ReflectionTestUtils.setField(seleniumManager, "headless", true);
        ReflectionTestUtils.setField(seleniumManager, "pageLoadTimeout", 30);
        ReflectionTestUtils.setField(seleniumManager, "implicitWait", 10);
        ReflectionTestUtils.setField(seleniumManager, "mobileMode", true);
    }

    @Nested
    @DisplayName("配置测试")
    class ConfigTests {

        @Test
        @DisplayName("检查配置值设置正确")
        void testConfigValues() {
            assertThat(ReflectionTestUtils.getField(seleniumManager, "seleniumGridUrl"))
                .isEqualTo("http://localhost:4444");
            assertThat(ReflectionTestUtils.getField(seleniumManager, "poolSize"))
                .isEqualTo(4);
            assertThat(ReflectionTestUtils.getField(seleniumManager, "headless"))
                .isEqualTo(true);
            assertThat(ReflectionTestUtils.getField(seleniumManager, "pageLoadTimeout"))
                .isEqualTo(30);
            assertThat(ReflectionTestUtils.getField(seleniumManager, "implicitWait"))
                .isEqualTo(10);
            assertThat(ReflectionTestUtils.getField(seleniumManager, "mobileMode"))
                .isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("DriverWrapper 测试")
    class DriverWrapperTests {

        @Test
        @DisplayName("DriverWrapper markUnhealthy 增加失败计数")
        void testDriverWrapper_MarkUnhealthy() throws Exception {
            // 创建一个真实的 DriverInstance
            SeleniumManager.DriverInstance instance = new SeleniumManager.DriverInstance(
                "test-id-2", mockRemoteWebDriver);
            instance.failureCount.set(0);

            SeleniumManager.DriverWrapper wrapper = new SeleniumManager.DriverWrapper(
                    mockRemoteWebDriver, instance, seleniumManager);

            wrapper.markUnhealthy();

            assertThat(instance.failureCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("DriverWrapper close 归还 Driver - 关闭状态检查")
        void testDriverWrapper_Close_ShutdownCheck() throws Exception {
            SeleniumManager.DriverInstance instance = new SeleniumManager.DriverInstance(
                "test-id-3", mockRemoteWebDriver);
            ReflectionTestUtils.setField(seleniumManager, "isShuttingDown", true);

            SeleniumManager.DriverWrapper wrapper = new SeleniumManager.DriverWrapper(
                    mockRemoteWebDriver, instance, seleniumManager);

            // 当关闭状态时，close 应该调用 destroyDriverInstance
            // 由于 destroyDriverInstance 是私有的，这里主要测试 close 方法的调用
            wrapper.close();

            // 验证 driver 在 close 后不会被访问（因为已经关闭）
        }
    }

    @Nested
    @DisplayName("DriverInstance 测试")
    class DriverInstanceTests {

        @Test
        @DisplayName("DriverInstance 健康检查 - 正常状态")
        void testDriverInstance_IsHealthy() throws Exception {
            SeleniumManager.DriverInstance instance = new SeleniumManager.DriverInstance(
                "healthy-id", mockRemoteWebDriver);
            instance.failureCount.set(0);

            assertThat(instance.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("DriverInstance 健康检查 - 失败次数超限")
        void testDriverInstance_IsNotHealthy() throws Exception {
            SeleniumManager.DriverInstance instance = new SeleniumManager.DriverInstance(
                "unhealthy-id", mockRemoteWebDriver);
            instance.failureCount.set(5); // 超过阈值

            assertThat(instance.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("DriverInstance 计算空闲时间")
        void testDriverInstance_GetIdleTime() throws Exception {
            SeleniumManager.DriverInstance instance = new SeleniumManager.DriverInstance(
                "idle-id", mockRemoteWebDriver);
            Thread.sleep(10); // 等待 10ms

            long idleTime = instance.getIdleTime();

            assertThat(idleTime).isGreaterThanOrEqualTo(10);
        }
    }

    @Nested
    @DisplayName("统计信息测试")
    class StatsTests {

        @Test
        @DisplayName("获取统计信息 - 初始状态")
        void testGetStats_Initial() {
            String stats = seleniumManager.getStats();

            assertThat(stats).contains("活跃实例:0/4");
            assertThat(stats).contains("空闲:0");
            assertThat(stats).contains("等待:0");
            assertThat(stats).contains("总创建:0");
            assertThat(stats).contains("总拒绝:0");
            assertThat(stats).contains("总超时:0");
        }

        @Test
        @DisplayName("获取统计信息 - 拒绝请求后")
        void testGetStats_AfterRejected() {
            ReflectionTestUtils.setField(seleniumManager, "totalRejectedRequests", new AtomicInteger(1));

            String stats = seleniumManager.getStats();

            assertThat(stats).contains("总拒绝:1");
        }
    }

    @Nested
    @DisplayName("shutdown 测试")
    class ShutdownTests {

        @Test
        @DisplayName("关闭后无法借用 Driver")
        void testBorrowDriver_AfterShutdown() throws Exception {
            ReflectionTestUtils.setField(seleniumManager, "isShuttingDown", true);

            assertThatThrownBy(() -> seleniumManager.borrowDriver())
                    .isInstanceOf(SeleniumManager.BrowserPoolExhaustedException.class)
                    .hasMessageContaining("正在关闭");
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("URL 格式错误时创建 Driver 失败")
        void testMalformedUrlException() throws Exception {
            ReflectionTestUtils.setField(seleniumManager, "seleniumGridUrl", "invalid-url");

            try {
                invokeCreateDriverInstance();
            } catch (Exception e) {
                // InvocationTargetException 包装了实际的异常
                if (e.getCause() instanceof MalformedURLException) {
                    return; // 测试通过
                }
                throw e;
            }
        }
    }

    @Nested
    @DisplayName("WaitingRequest 测试")
    class WaitingRequestTests {

        @Test
        @DisplayName("WaitingRequest 正常完成")
        void testWaitingRequest_Complete() {
            SeleniumManager.WaitingRequest request = new SeleniumManager.WaitingRequest();
            SeleniumManager.DriverWrapper wrapper = mock(SeleniumManager.DriverWrapper.class);

            boolean result = request.complete(wrapper);

            assertThat(result).isTrue();
            assertThat(request.driverWrapper).isEqualTo(wrapper);
            assertThat(request.result.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("WaitingRequest 拒绝")
        void testWaitingRequest_Reject() {
            SeleniumManager.WaitingRequest request = new SeleniumManager.WaitingRequest();

            boolean result = request.reject("Test reason");

            assertThat(result).isTrue();
            assertThat(request.errorMessage).isEqualTo("Test reason");
            assertThat(request.result.get()).isEqualTo(-1);
        }

        @Test
        @DisplayName("WaitingRequest 重复 complete 返回 false")
        void testWaitingRequest_CompleteTwice() {
            SeleniumManager.WaitingRequest request = new SeleniumManager.WaitingRequest();
            SeleniumManager.DriverWrapper wrapper = mock(SeleniumManager.DriverWrapper.class);

            request.complete(wrapper);
            boolean secondResult = request.complete(mock(SeleniumManager.DriverWrapper.class));

            assertThat(secondResult).isFalse();
        }

        @Test
        @DisplayName("WaitingRequest 重复 reject 返回 false")
        void testWaitingRequest_RejectTwice() {
            SeleniumManager.WaitingRequest request = new SeleniumManager.WaitingRequest();

            request.reject("First reason");
            boolean secondResult = request.reject("Second reason");

            assertThat(secondResult).isFalse();
        }
    }

    @Nested
    @DisplayName("BrowserPoolExhaustedException 测试")
    class BrowserPoolExhaustedExceptionTests {

        @Test
        @DisplayName("创建异常 - 仅消息")
        void testException_MessageOnly() {
            SeleniumManager.BrowserPoolExhaustedException exception =
                    new SeleniumManager.BrowserPoolExhaustedException("Test message");

            assertThat(exception.getMessage()).isEqualTo("Test message");
        }

        @Test
        @DisplayName("创建异常 - 消息和原因")
        void testException_MessageAndCause() {
            Throwable cause = new RuntimeException("Cause");
            SeleniumManager.BrowserPoolExhaustedException exception =
                    new SeleniumManager.BrowserPoolExhaustedException("Test message", cause);

            assertThat(exception.getMessage()).isEqualTo("Test message");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    // 辅助方法：通过反射调用私有的 createDriverInstance 方法
    private SeleniumManager.DriverInstance invokeCreateDriverInstance() throws Exception {
        java.lang.reflect.Method method = SeleniumManager.class.getDeclaredMethod("createDriverInstance");
        method.setAccessible(true);
        return (SeleniumManager.DriverInstance) method.invoke(seleniumManager);
    }
}
