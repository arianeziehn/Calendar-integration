
package de.iolite.apps.example.devices;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.iolite.app.api.device.access.Device;
import de.iolite.apps.example.controller.EnvironmentController;
import de.iolite.apps.example.controller.StorageController;
import de.iolite.data.DailyEvents;
import de.iolite.data.GoogleEvent;
import de.iolite.utilities.concurrency.scheduler.Scheduler;

public class SonosController {

	private static final class Configured implements State {

		@Nonnull
		private final Device sonosDevice;

		@Nonnull
		private final Scheduler taskScheduler;

		@Nonnull
		private final EnvironmentController environment;
		
		@Nonnull
		private final DailyEvents dailyEvents;
		
		@Nonnull 
		private final StorageController storageController;

		private Configured(@Nonnull final Device sonos, @Nonnull final Scheduler scheduler, @Nonnull final EnvironmentController environmentController, @Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
			this.sonosDevice = sonos;
			this.taskScheduler = scheduler;
			this.environment = environmentController;
			this.dailyEvents = dailyEvents;
			this.storageController = storageController;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setSonos(@Nonnull final SonosController context, @Nonnull final Device sonos, @Nonnull final Scheduler scheduler,
				@Nonnull final EnvironmentController environmentController, @Nonnull DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
			context.setState(new Configured(sonos, scheduler, environmentController, dailyEvents, storageController));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void playSongAt(@Nonnull final SonosController context, @Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
			
		List<Date> reminders = dailyEvents.getAlarm();
		
			if (!reminders.isEmpty()){
				for (Date d: reminders){
			
					final long millisToEvent = d.getTime() - System.currentTimeMillis();
					if (millisToEvent < 0) {
						LOGGER.warn("Cannot schedule timer for past date '%s'", d);
					} else {
						this.taskScheduler.schedule(this::addSong, millisToEvent, TimeUnit.MILLISECONDS);
						LOGGER.debug("Scheduled SONOS 'addSong' task in {}s", TimeUnit.MILLISECONDS.toSeconds(millisToEvent));
					}
				}
			} else {
				LOGGER.warn("No reminders. Sonos will not be scheduled!");
			}
		}

		private void addSong() {
			
			if (this.storageController.isSonosEnabled()){
				
				if (this.environment.isUserAtHome()) {
					LOGGER.debug("User is at home, adding song to SONOS");
					SonosMusic.playSong(this.sonosDevice, this.taskScheduler, this.storageController);
				} else {
					LOGGER.debug("User is not at home, SONOS song will not be added.");
				}
				
			} else {
				LOGGER.warn("Sonos is not enabled. Song will not be added.");
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return String.format("%s[device='%s']", getClass().getSimpleName(), this.sonosDevice.getIdentifier());
		}
	}

	private enum NotConfigured implements State {
		INSTANCE;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setSonos(@Nonnull final SonosController context, @Nonnull final Device sonos, @Nonnull final Scheduler scheduler,
				@Nonnull final EnvironmentController environmentController, @Nonnull DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
			context.setState(new Configured(sonos, scheduler, environmentController, dailyEvents, storageController));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void playSongAt(@Nonnull final SonosController context, @Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
			throw new IllegalStateException("Not configured");
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	private interface State {

		void setSonos(@Nonnull SonosController context, @Nonnull final Device sonos, @Nonnull final Scheduler scheduler,
				@Nonnull final EnvironmentController environmentController, @Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController);

		void playSongAt(@Nonnull SonosController context, @Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController);
	}

	@Nonnull
	private static final Logger LOGGER = LoggerFactory.getLogger(SonosController.class);

	@Nonnull
	private volatile State state = NotConfigured.INSTANCE;

	public void setSonos(@Nonnull final Device sonos, @Nonnull final Scheduler scheduler, @Nonnull final EnvironmentController environmentController, @Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
		Validate.notNull(sonos, "'sonos' must not be null");
		Validate.notNull(scheduler, "'scheduler' must not be null");
		Validate.notNull(environmentController, "'environmentController' must not be null");
		Validate.notNull(dailyEvents, "'dailyEvents' must not be null");
		Validate.notNull(storageController, "'storageController' must not be null");
		this.state.setSonos(this, sonos, scheduler, environmentController, dailyEvents, storageController);
	}

	public void playSongAt(@Nonnull final DailyEvents dailyEvents, @Nonnull final StorageController storageController) {
		Validate.notNull(dailyEvents, "'dailyEvents' must not be null");
		Validate.notNull(storageController, "'storagecontroller' must not be null");
		this.state.playSongAt(this, dailyEvents, storageController);
	}

	private void setState(@Nonnull final State newState) {
		this.state = newState;
		LOGGER.debug("Changed state in {}", toString());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("%s[state='%s']", getClass().getSimpleName(), this.state);
	}
}
