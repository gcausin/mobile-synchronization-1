using Generated.Sync.Model.System;
using MobileSyncModels.Model.System;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSyncModels.Services
{
    public interface IBaseModelService
    {
        Synchronization Synchronization { get; }
        User User { get; }
        User PublicUser { get; }
        string Username { get; set; }
        string Password { get; set; }
        bool IsOwnedByPublic(string userFk);
    }
}
