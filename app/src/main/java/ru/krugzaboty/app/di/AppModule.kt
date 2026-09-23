package ru.krugzaboty.app.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.krugzaboty.app.data.local.KrugDatabase
import ru.krugzaboty.app.data.local.dao.AppointmentDao
import ru.krugzaboty.app.data.local.dao.CareRecipientDao
import ru.krugzaboty.app.data.local.dao.DiaryDao
import ru.krugzaboty.app.data.local.dao.IntakeDao
import ru.krugzaboty.app.data.local.dao.MedicationDao
import ru.krugzaboty.app.data.local.dao.SubscriptionDao
import ru.krugzaboty.app.subscription.FakeSubscriptionDataSource
import ru.krugzaboty.app.subscription.SubscriptionDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // TODO(E1-03): SupportFactory(SQLCipher.getDatabasePass()) — ключ из Android Keystore
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): KrugDatabase =
        Room.databaseBuilder(ctx, KrugDatabase::class.java, KrugDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun recipientDao(db: KrugDatabase): CareRecipientDao = db.recipientDao()
    @Provides fun medicationDao(db: KrugDatabase): MedicationDao = db.medicationDao()
    @Provides fun intakeDao(db: KrugDatabase): IntakeDao = db.intakeDao()
    @Provides fun diaryDao(db: KrugDatabase): DiaryDao = db.diaryDao()
    @Provides fun appointmentDao(db: KrugDatabase): AppointmentDao = db.appointmentDao()
    @Provides fun subscriptionDao(db: KrugDatabase): SubscriptionDao = db.subscriptionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionModule {
    /**
     * Заменить на RuStorePayDataSource после сверки API Pay SDK (docs/05, docs/00 #5).
     * Fake: покупки «успешны», verify недоступен → приложение живёт в FREE, триггеры работают.
     */
    @Binds @Singleton
    abstract fun subscriptionDataSource(impl: FakeSubscriptionDataSource): SubscriptionDataSource
}
