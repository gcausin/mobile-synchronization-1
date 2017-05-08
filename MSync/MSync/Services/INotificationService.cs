using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public class NotificationEvent
    {
        public static readonly string Reset = "Reset";
        public static readonly string Synchronized = "Synchronized";
        public static readonly string InitiallySynchronized = "InitiallySynchronized";
        public static readonly string SynchronizationFailed = "SynchronizationFailed";
        public static readonly string PreSynchronization = "PreSynchronization";
        public static readonly string CredentialsChanged = "CredentialsChanged";
    }

    public interface INotificationService
    {
        void Subscribe(string notificationEvent, Action call);
        void Send(string notificationEvent);
    }
}
