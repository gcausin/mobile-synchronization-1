using Generated.MobileSynchronization;
using MobileSyncModels.Base;
using MobileSyncModels.Model.System;
using MobileSyncModels.Services;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Input;
using Xamarin.Forms;

[assembly: Xamarin.Forms.Dependency(typeof(ResetCommand))]
namespace MobileSyncModels.Services
{
    public class ResetCommand : ServiceProvider, IResetCommand
    {
        public Command Command { get; set; }

        public ResetCommand()
        {
            Command = new Command(Get<ISynchronizationService>().Reset, () => !SynchronizationInProgress);
            Get<INotificationService>().Subscribe(NotificationEvent.PreSynchronization, () => SetSynchronizationInProgress(true));
            Get<INotificationService>().Subscribe(NotificationEvent.SynchronizationFailed, () => SetSynchronizationInProgress(false));
            Get<INotificationService>().Subscribe(NotificationEvent.Synchronized, () => SetSynchronizationInProgress(false));
        }

        private bool SynchronizationInProgress { get; set; }

        private void SetSynchronizationInProgress(bool inProgress)
        {
            SynchronizationInProgress = inProgress;
            Command.ChangeCanExecute();
        }
    }
}
