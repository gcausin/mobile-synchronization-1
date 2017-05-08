using MobileClient.RecipeExample.SimpleSync;
using MobileSyncModels.Services;
using System;
using System.Diagnostics;
using System.Linq;

namespace MobileSync.Example.Generic
{
    public class SimpleSyncViewModel : BaseViewModel
    {
        public SimpleSyncViewModel()
        {
            Get<INotificationService>().Subscribe(NotificationEvent.Synchronized, () => RaisePropertyChanged(nameof(Result)));
            Get<INotificationService>().Subscribe(NotificationEvent.Reset, () => RaisePropertyChanged(nameof(Result)));

            SynchronizationCommand.ParameterEnhancer = (sp, p) =>
            {
                sp.Refresh = () =>
                {
                    RaisePropertyChanged(nameof(Result));
                };

                return sp;
            };
        }

        public string Result
        {
            get
            {
                return Get<ISynchronizationService>()
                        .AllEntities
                        .Select(t => t.Name + "(" + Get<IDatabaseConnection>().Connection.ExecuteScalar<int>("select count(*) from [" + t.Name + "]") + ")")
                        .Aggregate((i, j) => i + Environment.NewLine + j);
            }
        }
    }
}
