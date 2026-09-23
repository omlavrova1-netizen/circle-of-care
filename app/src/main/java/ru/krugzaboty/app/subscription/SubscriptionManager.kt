package ru.krugzaboty.app.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.krugzaboty.app.data.local.dao.SubscriptionDao
import ru.krugzaboty.app.data.local.entity.SubStatus
import ru.krugzaboty.app.data.local.entity.SubscriptionState
import javax.inject.Inject
import javax.inject.Singleton

enum class Sku(val id: String) {
    MONTH("krug_premium_month"), YEAR("krug_premium_year"), LIFETIME("krug_premium_lifetime");
    companion object { fun from(id: String) = entries.firstOrNull { it.id == id } }
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data class Failed(val reason: String) : PurchaseResult // cancelled|network|declined|unknown
}

/**
 * Реализация RuStorePayDataSource пишется после сверки API (docs/05, docs/00 #5).
 * В интерфейсе нет ничего, что зависело бы от конкретного SDK.
 */
interface SubscriptionDataSource {
    suspend fun purchase(sku: Sku): PurchaseResult
    /** Верификация статуса в магазине; null — недоступно/ошибка сети. */
    suspend fun verify(): VerifiedPurchase?
    suspend fun restore(): VerifiedPurchase?
}

data class VerifiedPurchase(val sku: Sku, val expiresAt: Long?, val tokenHash: String)

@Singleton
class SubscriptionManager @Inject constructor(
    private val dao: SubscriptionDao,
    private val dataSource: SubscriptionDataSource,
) {
    private val _state = MutableStateFlow(SubscriptionState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    private val offlineGraceMs = 72L * 60 * 60 * 1000 // docs/05 §3

    suspend fun refresh(forceVerify: Boolean = false) {
        val verified = dataSource.verify()
        if (verified != null) {
            persist(verified.toState(verified = true))
            return
        }
        val cached = dao.get() ?: return
        if (!forceVerify && System.currentTimeMillis() - cached.lastVerifiedAt <= offlineGraceMs) {
            _state.value = cached.copy(source = "CACHED")
        } else if (cached.status.premium()) {
            // кэш истёк — мягкий лок premium, Free работает (FR-29)
            _state.value = cached.copy(status = SubStatus.UNKNOWN_OFFLINE)
        }
    }

    /**
     * Единый публичный предикат «можно ли пользоваться Premium сейчас»:
     * активные статусы + UNKNOWN_OFFLINE в пределах 72-часового офлайн-грейса.
     * Все проверки лимитов (paywall-гейты) должны звать его, а не перебирать статусы.
     */
    fun isPremiumUsable(): Boolean {
        val s = _state.value
        return s.status.premium() || (s.status == SubStatus.UNKNOWN_OFFLINE &&
            System.currentTimeMillis() - s.lastVerifiedAt <= offlineGraceMs)
    }

    private fun SubStatus.premium() = when (this) {
        SubStatus.TRIAL_ACTIVE, SubStatus.ACTIVE_MONTH, SubStatus.ACTIVE_YEAR,
        SubStatus.LIFETIME, SubStatus.GRACE -> true
        else -> false
    }

    private fun VerifiedPurchase.toState(verified: Boolean) = SubscriptionState(
        status = when (sku) {
            Sku.MONTH -> SubStatus.ACTIVE_MONTH
            Sku.YEAR -> SubStatus.ACTIVE_YEAR
            Sku.LIFETIME -> SubStatus.LIFETIME
        },
        sku = sku.id,
        expiresAt = expiresAt,
        purchaseTokenHash = tokenHash,
        lastVerifiedAt = System.currentTimeMillis(),
        source = if (verified) "VERIFIED" else "CACHED",
    )

    private suspend fun persist(s: SubscriptionState) {
        dao.upsert(s); _state.value = s
    }

    suspend fun purchase(sku: Sku): PurchaseResult {
        val r = dataSource.purchase(sku)
        if (r is PurchaseResult.Success) refresh(forceVerify = true)
        return r
    }

    suspend fun restore(): Boolean {
        val v = dataSource.restore() ?: return false
        persist(v.toState(true)); return true
    }

    /** Проброс статуса из триального/подписочного колбэка Pay SDK. */
    suspend fun onTrialStarted(sku: Sku, trialEndsAt: Long) = persist(
        // Именованные аргументы: у SubscriptionState первый позиционный — id: Int,
        // позиционная сборка путала порядок полей и не компилировалась.
        SubscriptionState(
            status = SubStatus.TRIAL_ACTIVE,
            sku = sku.id,
            expiresAt = trialEndsAt,
            lastVerifiedAt = System.currentTimeMillis(),
            source = "VERIFIED",
        ),
    )
}

/** Разработка/моки до подключения RuStore Pay SDK. */
class FakeSubscriptionDataSource @Inject constructor() : SubscriptionDataSource {
    override suspend fun purchase(sku: Sku): PurchaseResult = PurchaseResult.Success
    override suspend fun verify(): VerifiedPurchase? = null
    override suspend fun restore(): VerifiedPurchase? = null
}
