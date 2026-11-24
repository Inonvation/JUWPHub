package edu.jxslu.schedule

import edu.jxslu.schedule.data.kqcx.KqcxBikeClient
import edu.jxslu.schedule.domain.BikeFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * 附近单车接口客户端的失败分类（DESIGN §4.23）。
 *
 * 判断顺序反了就会把超时吃成「网络不可达」，用户按提示反复检查网络也修不好——
 * 这几条就是在钉这个顺序。
 *
 * 2026-11-24（iOS 移植 P1）：超时的判别不再靠 `java.net.SocketTimeoutException`
 * （JVM 类型，iOS 上没有），改成客户端自己的 [KqcxBikeClient.KqcxTimeoutException]。
 * 后面两条仍然喂真实的 JVM 异常，验的是「不认识的异常一律按网络失败」这条兜底。
 */
class KqcxBikeClientTest {

    @Test
    fun `超时归超时`() {
        assertEquals(
            BikeFailure.Timeout,
            KqcxBikeClient.classify(KqcxBikeClient.KqcxTimeoutException(null)),
        )
    }

    @Test
    fun `连不上归网络`() {
        assertEquals(BikeFailure.Network, KqcxBikeClient.classify(IOException("boom")))
        assertEquals(
            BikeFailure.Network,
            KqcxBikeClient.classify(UnknownHostException("api.kvcoogo.com")),
        )
    }

    @Test
    fun `非 2xx 归服务端`() {
        assertEquals(
            BikeFailure.Service,
            KqcxBikeClient.classify(KqcxBikeClient.ServiceHttpException(502)),
        )
    }

    @Test
    fun `意料之外的异常也算一次失败，不往外抛`() {
        assertEquals(BikeFailure.Network, KqcxBikeClient.classify(IllegalStateException("boom")))
    }
}