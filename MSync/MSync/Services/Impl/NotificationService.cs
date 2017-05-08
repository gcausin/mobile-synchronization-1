using MobileSyncModels.Services;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Xamarin.Forms;

[assembly: Xamarin.Forms.Dependency(typeof(NotificationService))]
namespace MobileSyncModels.Services
{
    public class NotificationService : INotificationService
    {
        public void Send(string notificationEvent)
        {
            MessagingCenter.Send(this, notificationEvent);
        }

        public void Subscribe(string notificationEvent, Action call)
        {
            MessagingCenter.Subscribe<NotificationService>(call, notificationEvent, p => call());
        }
    }
}
