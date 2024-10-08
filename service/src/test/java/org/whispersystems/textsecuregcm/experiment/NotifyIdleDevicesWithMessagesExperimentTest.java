package org.whispersystems.textsecuregcm.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.whispersystems.textsecuregcm.entities.MessageProtos;
import org.whispersystems.textsecuregcm.identity.IdentityType;
import org.whispersystems.textsecuregcm.push.IdleDeviceNotificationScheduler;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.storage.MessagesManager;
import reactor.core.publisher.Flux;

class NotifyIdleDevicesWithMessagesExperimentTest extends IdleDevicePushNotificationExperimentTest {

  private IdleDeviceNotificationScheduler idleDeviceNotificationScheduler;
  private MessagesManager messagesManager;

  private NotifyIdleDevicesWithMessagesExperiment experiment;

  @BeforeEach
  void setUp() {
    idleDeviceNotificationScheduler = mock(IdleDeviceNotificationScheduler.class);
    messagesManager = mock(MessagesManager.class);

    experiment = new NotifyIdleDevicesWithMessagesExperiment(idleDeviceNotificationScheduler,
        messagesManager,
        Clock.fixed(CURRENT_TIME, ZoneId.systemDefault()));
  }

  @Override
  protected IdleDevicePushNotificationExperiment getExperiment() {
    return experiment;
  }

  @ParameterizedTest
  @MethodSource
  void isDeviceEligible(final Account account,
      final Device device,
      final boolean hasUrgentMessage,
      final boolean expectEligible) {

    when(messagesManager.getMessagesForDeviceReactive(account.getIdentifier(IdentityType.ACI), device, false))
        .thenReturn(Flux.just(MessageProtos.Envelope.newBuilder().setUrgent(hasUrgentMessage).build()));

    assertEquals(expectEligible, experiment.isDeviceEligible(account, device).join());
  }

  private static List<Arguments> isDeviceEligible() {
    final List<Arguments> arguments = new ArrayList<>();

    final Account account = mock(Account.class);
    when(account.getIdentifier(IdentityType.ACI)).thenReturn(UUID.randomUUID());
    when(account.getNumber()).thenReturn(PhoneNumberUtil.getInstance().format(
        PhoneNumberUtil.getInstance().getExampleNumber("US"), PhoneNumberUtil.PhoneNumberFormat.E164));

    {
      // Idle primary device with push token and urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getApnId()).thenReturn("apns-token");
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.minus(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION).toEpochMilli());

      arguments.add(Arguments.of(account, device, true, true));
    }

    {
      // Idle non-primary device with push token and urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(false);
      when(device.getApnId()).thenReturn("apns-token");
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.minus(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION).toEpochMilli());

      arguments.add(Arguments.of(account, device, true, false));
    }

    {
      // Idle primary device missing push token, but with messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.minus(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION).toEpochMilli());

      arguments.add(Arguments.of(account, device, true, false));
    }

    {
      // Idle primary device missing push token and with no urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.minus(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION).toEpochMilli());

      arguments.add(Arguments.of(account, device, false, false));
    }

    {
      // Idle primary device with push token, but no urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.minus(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION).toEpochMilli());
      when(device.getApnId()).thenReturn("apns-token");

      arguments.add(Arguments.of(account, device, false, false));
    }

    {
      // Active primary device with push token and urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.toEpochMilli());
      when(device.getApnId()).thenReturn("apns-token");

      arguments.add(Arguments.of(account, device, true, false));
    }

    {
      // Active primary device missing push token, but with urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.toEpochMilli());

      arguments.add(Arguments.of(account, device, true, false));
    }

    {
      // Active primary device missing push token and with no urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.toEpochMilli());

      arguments.add(Arguments.of(account, device, false, false));
    }

    {
      // Active primary device with push token, but no urgent messages
      final Device device = mock(Device.class);
      when(device.isPrimary()).thenReturn(true);
      when(device.getLastSeen()).thenReturn(CURRENT_TIME.toEpochMilli());
      when(device.getApnId()).thenReturn("apns-token");

      arguments.add(Arguments.of(account, device, false, false));
    }

    return arguments;
  }

  @ParameterizedTest
  @MethodSource
  void isIdle(final Duration idleDuration, final boolean expectIdle) {
    final Device device = mock(Device.class);
    when(device.getLastSeen()).thenReturn(CURRENT_TIME.minus(idleDuration).toEpochMilli());

    assertEquals(expectIdle, experiment.isIdle(device));
  }

  private static List<Arguments> isIdle() {
    return List.of(
        Arguments.of(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION, true),
        Arguments.of(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION.plusMillis(1), true),
        Arguments.of(NotifyIdleDevicesWithMessagesExperiment.MIN_IDLE_DURATION.minusMillis(1), false),
        Arguments.of(NotifyIdleDevicesWithMessagesExperiment.MAX_IDLE_DURATION, false),
        Arguments.of(NotifyIdleDevicesWithMessagesExperiment.MAX_IDLE_DURATION.plusMillis(1), false),
        Arguments.of(NotifyIdleDevicesWithMessagesExperiment.MAX_IDLE_DURATION.minusMillis(1), true)
    );
  }

  @Test
  void applyExperimentTreatment() {
    final Account account = mock(Account.class);
    final Device device = mock(Device.class);

    experiment.applyExperimentTreatment(account, device);

    verify(idleDeviceNotificationScheduler)
        .scheduleNotification(account, device, NotifyIdleDevicesWithMessagesExperiment.PREFERRED_NOTIFICATION_TIME);
  }
}
