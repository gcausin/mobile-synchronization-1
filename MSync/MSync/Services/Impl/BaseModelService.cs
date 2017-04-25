using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Generated.Sync.Model.System;
using MobileSyncModels.Model.System;
using Xamarin.Forms;
using MobileSyncModels.Services;
using System.ComponentModel;
using System.Linq.Expressions;
using MobileSyncModels.Base;
using SQLiteNetExtensions.Extensions;

[assembly: Xamarin.Forms.Dependency(typeof(BaseModelService))]
namespace MobileSyncModels.Services
{
    public class BaseModelService : ServiceProvider, IBaseModelService, INotifyPropertyChanged
    {
        private Synchronization synchronization;
        public Synchronization Synchronization
        {
            get
            {
                if (synchronization == null)
                {
                    synchronization = Get<IDatabaseConnection>().Connection.Query<Synchronization>("select * from [Synchronization]").FirstOrDefault();

                    if (synchronization == null)
                    {
                        synchronization = new Synchronization();
                        synchronization.Version = Get<IDatabaseConnection>().Version;

                        Get<IDatabaseConnection>().Connection.Insert(synchronization);
                    }
                }

                return synchronization;
            }

            set
            {
                synchronization = null;
            }
        }

        private User user;
        public User User
        {
            get
            {
                return user ?? (user = FindUser(Synchronization?.Username));
            }
        }

        private User publicUser;
        public User PublicUser
        {
            get
            {
                return publicUser ?? (publicUser = FindUser("public"));
            }
        }

        public string Username
        {
            get { return Synchronization.Username; }
            set
            {
                Synchronization.Username = value;
                RaisePropertyChanged(nameof(Username));
                Get<INotificationService>().Send(NotificationEvent.CredentialsChanged);
            }
        }

        public string Password
        {
            get { return Synchronization.Password; }
            set
            {
                Synchronization.Password = value;
                RaisePropertyChanged(nameof(Password));
                Get<INotificationService>().Send(NotificationEvent.CredentialsChanged);
            }
        }

        public bool IsOwnedByPublic(string userFk)
        {
            return userFk == PublicUser?.Pk;
        }

        public BaseModelService()
        {
            Get<INotificationService>().Subscribe(NotificationEvent.Reset, () =>
            {
                synchronization = null;
                publicUser = user = null;
                RaisePropertyChanged(nameof(Username));
                RaisePropertyChanged(nameof(Password));
                Get<INotificationService>().Send(NotificationEvent.CredentialsChanged);
            });
        }

        private User FindUser(string username)
        {
            if (username == null)
            {
                return null;
            }

            User user = Get<IDatabaseConnection>().Connection.Query<User>("select * from [User] where Name like ?",
                                                                     new object[] { username }).FirstOrDefault();

            Get<IDatabaseConnection>().Connection.GetChildren(user, true);

            return user;
        }

        public event PropertyChangedEventHandler PropertyChanged;

        protected void RaisePropertyChanged(string propertyName)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }
    }
}
