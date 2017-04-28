using Generated.MobileSynchronization;
using MobileSyncModels.Base;
using MobileSyncModels.Services;
using System;
using Xamarin.Forms;

[assembly: Xamarin.Forms.Dependency(typeof(SynchronizationCommand))]
namespace MobileSyncModels.Services
{
    public class SynchronizationCommand : ServiceProvider, ISynchronizationCommand
    {
        public Command Command { get; set; }

        public Func<SynchronizationParameters, SynchronizationParameters> ParameterEnhancer { get; set; } = p => p;

        public SynchronizationCommand()
        {
            Command = new Command(Do, Can);
            Get<INotificationService>().Subscribe(NotificationEvent.CredentialsChanged, () => Command.ChangeCanExecute());
        }

        private bool Can()
        {
            return !SynchronizationInProgress &&
                   !string.IsNullOrWhiteSpace(Get<IBaseModelService>().Username) &&
                   !string.IsNullOrWhiteSpace(Get<IBaseModelService>().Password);
        }

        private void Do()
        {
            Get<IDatabaseConnection>().Connection.Update(Get<IBaseModelService>().Synchronization);

            SetSynchronizationInProgress(true);

            Get<ISynchronizationService>().Synchronize(ParameterEnhancer(new SynchronizationParameters
            {
                Username = Get<IBaseModelService>().Username,
                Password = Get<IBaseModelService>().Password,
                Server = GeneratedConstants.Server,
                FinalAction = () =>
                {
                    SetSynchronizationInProgress(false);
                    Application.Current.MainPage.DisplayAlert("Synchronization", "Success", "Ok");
                },
                ExceptionHandler = exception =>
                {
                    SetSynchronizationInProgress(false);
                    Application.Current.MainPage.DisplayAlert(
                        "Synchronization",
                        "Problem" + Environment.NewLine + Environment.NewLine + exception.Message + Environment.NewLine,
                        "Ok");
                },
            }));
        }

        private bool SynchronizationInProgress { get; set; }
        private void SetSynchronizationInProgress(bool inProgress)
        {
            SynchronizationInProgress = inProgress;
            Command.ChangeCanExecute();
        }
    }
}
