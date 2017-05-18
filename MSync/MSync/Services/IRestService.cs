using Model.Base;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public class DownloadResult<T> where T : AbstractEntity, new()
    {
        public List<T> Items { get; set; }
        public bool FetchNextPage { get; set; }
    }

    public interface IRestService<T> where T : AbstractEntity, new()
    {
        Task<DownloadResult<T>> DownloadAsync(DateTime lastSyncTime, int pageCount, int pageSize);
        Task UploadAsync(object item, Action<Exception> exceptionHandler, string urlPath = null);
        string ListProperty(Type type);
    }
}
