package com.linkedin.clustermanager.examples;

import com.linkedin.clustermanager.NotificationContext;
import com.linkedin.clustermanager.examples.OnlineOfflineStateModelFactory.OnlineOfflineStateModel;
import com.linkedin.clustermanager.model.Message;
import com.linkedin.clustermanager.participant.statemachine.StateModel;
import com.linkedin.clustermanager.participant.statemachine.StateModelFactory;

public class LeaderStandbyStateModelFactory extends StateModelFactory<StateModel> {
	int _delay;

	public LeaderStandbyStateModelFactory(int delay) {
		_delay = delay;
	}

	@Override
	public StateModel createNewStateModel(String stateUnitKey) {
		LeaderStandbyStateModel stateModel = new LeaderStandbyStateModel();
		stateModel.setDelay(_delay);
		return stateModel;
	}

	public static class LeaderStandbyStateModel extends StateModel {
		int _transDelay = 0;

		public void setDelay(int delay) {
			_transDelay = delay > 0 ? delay : 0;
		}

		public void onBecomeSlaveFromOffline(Message message,
				NotificationContext context) {
			System.out.println("DummyStateModel.onBecomeSlaveFromOffline()");
			sleep();
		}

		private void sleep() {
			try {
				Thread.sleep(_transDelay);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}