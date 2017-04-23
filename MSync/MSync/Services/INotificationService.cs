using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public enum NotificationEvent
    {
        Reset,
        Synchronized,
        SynchronizationFailed,
        PreSynchronization,
        CredentialsChanged
    }

    public interface INotificationService
    {
        void Subscribe(NotificationEvent notificationEvent, Action call);
        void Send(NotificationEvent notificationEvent);
    }
}
