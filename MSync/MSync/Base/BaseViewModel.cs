using Generated.Sync.Model.System;
using MobileSyncModels.Base;
using MobileSyncModels.Services;
using Model.Base;
using SQLite;
using System;
using Xamarin.Forms;

namespace MobileClient.RecipeExample.SimpleSync
{
    public class BaseViewModel : NotifyPropertyChanged, IServiceProviderService
    {
        public ISynchronizationCommand SynchronizationCommand { get; set; }
        public IResetCommand Reset { get; set; }
        public IBaseModelService BaseModel { get; set; }
        public IServiceProviderService ServiceProvider { get; } = DependencyService.Get<IServiceProviderService>();
        public SQLiteConnection Connection { get { return Get<IDatabaseConnection>().Connection;  } }

        protected BaseViewModel()
        {
            SynchronizationCommand = Get<ISynchronizationCommand>();
            Reset = Get<IResetCommand>();
            BaseModel = Get<IBaseModelService>();
        }

        protected void Upsert(IUpsertable record)
        {
            record.IsPending = true;
            record.ModifiedDate = new DateTime(DateTime.Now.ToUniversalTime().Ticks / 10000 * 10000);

            if (record.IsNew)
            {
                Get<IDatabaseConnection>().Connection.Insert(record);
                record.IsNew = false;
            }
            else
            {
                Get<IDatabaseConnection>().Connection.Update(record);
            }
        }

        protected void Delete(IUpsertable record)
        {
            Upsert(new DeletedRecord
            {
                EntityName = record.GetType().Name,
                EntityPk = record.Pk,
                UserFk = Get<IBaseModelService>().User.Pk,
                IsNew = true,
            });

            Get<IDatabaseConnection>().Connection.Delete(record);
        }

        public T Get<T>() where T : class
        {
            return ServiceProvider.Get<T>();
        }
    }
}
