using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public interface IServiceProviderService
    {
        T Get<T>() where T : class;
    }
}
