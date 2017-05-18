using Generated.MobileSynchronization;
using Generated.Sync.Model.System;
using MobileSyncModels.Base;
using MobileSyncModels.Services;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using Xamarin.Forms;

[assembly: Xamarin.Forms.Dependency(typeof(SynchronizationCommand))]
namespace MobileSyncModels.Services
{
    public class SynchronizationCommand : ServiceProvider, ISynchronizationCommand
    {
        public Command<string> Command { get; set; }

        public Func<SynchronizationParameters, string, SynchronizationParameters> ParameterEnhancer { get; set; } = (sp, p) => sp;

        public SynchronizationCommand()
        {
            Command = new Command<string>(Do, Can);
            Get<INotificationService>().Subscribe(NotificationEvent.CredentialsChanged, () => Command.ChangeCanExecute());
        }

        private bool Can(string parameter)
        {
            return !SynchronizationInProgress &&
                   !string.IsNullOrWhiteSpace(Get<IBaseModelService>().Username) &&
                   !string.IsNullOrWhiteSpace(Get<IBaseModelService>().Password);
        }

        private void Do(string parameter)
        {
            Get<IDatabaseConnection>().Connection.Update(Get<IBaseModelService>().Synchronization);
            List<User> users = Get<IDatabaseConnection>().Connection.Query<User>("select * from User");

            SetSynchronizationInProgress(true);

            Get<ISynchronizationService>().Synchronize(ParameterEnhancer(new SynchronizationParameters
            {
                Username = Get<IBaseModelService>().Username,
                Password = Get<IBaseModelService>().Password,
                Server = GeneratedConstants.Server,
                FinalAction = () =>
                {
                    SetSynchronizationInProgress(false);

                    if (users.Count == 0 && Get<IDatabaseConnection>().Connection.Query<User>("select * from User").Count > 0)
                    {
                        Get<INotificationService>().Send(NotificationEvent.InitiallySynchronized);
                    }

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
            }, parameter));
        }

        private Stopwatch watch;
        private bool SynchronizationInProgress { get; set; }
        private void SetSynchronizationInProgress(bool inProgress)
        {
            if (GeneratedConstants.LogDebug)
            {
                if (inProgress)
                {
                    watch = new Stopwatch();
                    watch.Start();
                }
                else
                {
                    Debug.WriteLine("Total synchronization needed " + watch.Elapsed);
                }
            }

            SynchronizationInProgress = inProgress;
            Command.ChangeCanExecute();
        }
    }
}
