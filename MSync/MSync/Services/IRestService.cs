using Model.Base;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public interface IRestService<T> where T : AbstractEntity, new()
    {
        Task<List<T>> DownloadAsync(DateTime lastSyncTime, int pageCount, int pageSize);
        Task UploadAsync(object item, Action<Exception> exceptionHandler, string urlPath = null);
        string ListProperty(Type type);
    }
}
